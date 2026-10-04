# 星落平原（SFP-main）功能明细

> 本文档记录**每个功能的实现细节**：入口 → 调用链 → 流程 → 数据与配置。
>
> **约定：新增功能必须同步写进本文档。** 至少补上这四项：
> ① 入口（命令 / 菜单按钮 / 事件）② 调用链（哪个类调哪个类）③ 流程与边界情况 ④ 涉及的命令、
> 权限、配置文件与数据文件。若只改了已有功能，把该章节对应部分一并更新。
> 文末的「新增功能检查清单」是要照着走一遍的。

- 插件形态：Paper 插件（`paper-plugin.yml`），Java 25，编译目标 paper-api 26.3
- 插件名（`name`）：`SFP-main` —— 决定日志前缀与数据目录（`plugins/SFP-main/`）
- 主类：`cn.starfallplain.sfpmain.SfpMain`（包名 `cn.starfallplain.sfpmain`、主类 `SfpMain`）
- 元信息文件：`src/main/resources/paper-plugin.yml`（`version` 由 `${version}` 过滤）

---

## 0. 全局

### 0.1 启动流程（`SfpMain#onEnable`）

```
1) configManager = new ConfigManager(this)
     └─ 读 config.yml（GlobalConfig，走 Bukkit 的 getConfig）+ messages.yml（Messages）
     └─ loadAll()：依次 new 出 Menu/Clean/TrashBin/Chair/Teleport/Bot 六个 Config
        （每个 Config 构造时就 load() → onLoaded()，把 yml 的值缓存成字段）
2) 按开关装配模块（顺序有意义）：
     setupTrashBin()     垃圾桶必须先于扫地（扫地要把物品塞进垃圾桶）
     setupFloorClean()   扫地：new FloorCleanManager（内部 runTaskTimer 每秒 tick）
     setupChair()        椅子：new ChairManager + 注册 ChairListener
     setupTeleport()     传送：new TeleportManager（内含 SQLite）→ new TpaManager
                         → 注册 TeleportListener / TeleportGuiListener / TpaListener
     setupBot()          假人：new BotManager，并 runTask 延后一 tick 执行 restoreOnStart()
     setupMenu()         菜单：注册 MenuListener（菜单本身是懒构建的，无状态）
3) registerCommands()   注册全部命令（依赖上面各 manager 已就位，故必须放最后）
4) setupPlaceholders()  PAPI 可用时注册 %stf_cleantime% / %stf_cleantime_plain%
5) 打印「功能状态」汇总（ConfigManager#describeState）
```

`onDisable`：`trashBinManager.save()` → `teleportManager.shutdown()`（关 DB）→
`tpaManager.shutdown()`（取消过期任务）→ `botManager.saveData() + shutdown()`。

### 0.2 配置体系

| 文件 | 对应类 | 说明 |
|---|---|---|
| `config.yml` | `GlobalConfig` | 全局：`debug`、`currency-name`。**不继承 AbstractConfig**，直接走 `plugin.getConfig()` |
| `menu.yml` | `MenuConfig` | 主菜单标题/尺寸/边框/按钮 |
| `clean.yml` | `CleanConfig` | 自动扫地 |
| `trashbin.yml` | `TrashBinConfig` | 垃圾桶 |
| `chair.yml` | `ChairConfig` | 椅子 |
| `teleport.yml` | `TeleportConfig` | 传送（back/home/warp/tpa + 数据库） |
| `bot.yml` | `BotConfig` | 假人 |
| `tab.yml` | `TabConfig` | Tab 列表头部/底部（display 包） |
| `scoreboard.yml` | `ScoreboardConfig` | 计分板（display 包） |
| `messages.yml` | `Messages` | 所有面向玩家的文案（**不继承 AbstractConfig**） |

规则：
- 每个功能包一份配置，**包内一切可调项都从该文件读**，读取逻辑下沉到包内 `XxxConfig`。
- 键既可写全路径（`teleport.delay-seconds`）也可写短路径（`delay-seconds`），
  `AbstractConfig#normalize()` 负责兼容。
- 取值顺序：配置文件 → 代码内兜底默认值。所以**删掉某个键不会报错，只是回退到默认值**。
- 占位符统一用花括号 `{key}`（不要用 `%key%`，会与 PAPI 冲突）。
- 文本渲染优先 MiniMessage；`Messages.convertLegacy()` 兼容传统色码 `&a` / `§a`。

**启动时会自动补齐缺失键**：`AbstractConfig#mergeDefaults()` 用 jar 内同名 yml 做 defaults，
`copyDefaults(true)` 后立刻 `save()`。注意它**只补缺失键、不覆盖已存在的键** ——
所以改了 `resources/*.yml` 里**已存在**键的默认值后，服务器上的老配置文件**不会更新**，
必须手工同步（详见 §9）。

### 0.3 命令注册机制

Paper 插件不支持 `plugin.yml` 的 `commands` 段，全部命令在
`SfpMain#registerCommands()` 里通过
`getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, ...)` 注册。

- 所有命令实现 `io.papermc.paper.command.brigadier.BasicCommand`。
- **`BasicCommand#execute` 拿不到命令标签**，所以 `/home /sethome /delhome /homes`、
  `/warp /setwarp /delwarp /warps`、`/tpa /tpahere /tpaccept /tpdeny`
  都是「一个标签注册一个实例，用构造参数 `action` 区分行为」。
- **刻意不实现 `BasicCommand#permission()`**：那样无权限者眼里命令直接「不存在」、给不出提示；
  改为各 `execute` 内手动 `hasPermission` 并回 `common.no-permission`。
  代价是无权限的人在补全里仍能看到命令。
- 模块被关闭时同名命令**仍然注册**，统一回「该功能当前未启用」，避免玩家以为命令不存在。

已注册命令：`menu`(别名 `m`)、`trashbin`、`back`、`home`、`sethome`、`delhome`、`homes`、
`warp`、`setwarp`、`delwarp`、`warps`、`tpa`、`tpahere`、`tpaccept`、`tpdeny`、`tpaui`、`bot`、
`menuui`、`sfp`。

### 0.4 权限节点（`paper-plugin.yml` 的 `permissions` 段）

| 节点 | 默认 | 用于 |
|---|---|---|
| `sfpmenu.player` | true | 打开菜单、`/trashbin` |
| `sfpmenu.teleport` | true | `/back` `/home` `/warp` `/tpa` 等全部传送命令 |
| `sfpmenu.home.bypass-limit` | false | 绕过家数量上限 |
| `sfpmenu.warp.set` | op | `/setwarp`（也可在 teleport.yml 改空=人人可建） |
| `sfpmenu.warp.delete` | op | `/delwarp` |
| `sfpmenu.admin` | op | `/sfp`（reload / status / db / test） |
| `sfpmenu.bot` | op | `/bot` |

### 0.5 调试与自检（`debug/` 包）

| 命令 | 实现 | 作用 |
|---|---|---|
| `/sfp reload` | `SfpCommand` → `plugin.reloadAll()` | 重载配置。**开关类改动仍需重启**（监听器注册不可逆） |
| `/sfp status` | `SfpCommand` + `SelfTest#printRuntime` | 模块开关 + 各模块实时数据 |
| `/sfp db [...]` | `DbDebug` | 概览 / `tables` / `homes` / `warps` / `back` / `check` / `checkpoint` |
| `/sfp test` | `SelfTest#run` | 五段自检：配置、菜单、数据层、传送内容、功能模块 |

- 输出文案**写死在 `debug` 包内**（诊断条目多且高度动态，不进 messages.yml），门槛 `sfpmenu.admin`。
- `/sfp db` 全是只读诊断，**刻意不提供执行任意 SQL**（前缀白名单挡不住 `SELECT 1; DROP TABLE x`
  这类多语句）。`checkpoint` 是唯一的维护动作（把 WAL 合并回主库，便于直接拷贝 .db）。
- 自检里唯一会写数据的是数据层「增删改查回归」：用哨兵 UUID + 哨兵名称，
  `finally` 里清理，不碰真实玩家数据。
- 动作解析共用 `MenuManager#resolveSlotActions / resolveAction`，
  所以**自检报出的按钮绑定就是玩家实际点击的结果**。

---

## 1. 主菜单（menu 包）

**入口**：`/menu`、`/m`、菜单内子界面的「返回主菜单」按钮。

```
MenuCommand#execute
  → 玩家判断 → sfpmenu.player 判断
  → MenuManager.openMainMenu(plugin, player)
       ├─ 打开权限（menu.yml 的 permission）校验
       ├─ menu.yml 的 enabled 校验
       └─ createMainMenu() → player.openInventory(...)
```

**`createMainMenu` 构建过程**：
1. `MenuHolder`（携带 `slotActions` 映射 + MenuConfig）
2. 尺寸 = `rows * 9`（menu.yml 的 `rows`，代码夹在 1~6）
3. `applyBorder()`：按 `border.materials` 循环填充边框格（第 0/末行、第 0/末列），
   **只填 `getItem(slot) == null` 的格子**，所以按钮永远覆盖边框
4. 顶栏玩家头颅（`player-head.enabled` / `player-head.slot`）
5. 遍历 `buttons.*`，对每个按钮做三重判断后放置：
   `isFeatureEnabled(id)`（绑定模块的开关）→ `button.isVisible()`（按钮自身 enabled）
   → 槽位在 `[0, size)` 内（越界只记 warning 并跳过）

**按钮 → 动作**（`MenuManager#resolveAction`，`MenuHolder` 里是 `cmd:` 前缀的内部动作串）：

| 按钮 id | 槽位 | 图标 | 动作 |
|---|---|---|---|
| `back` | 19 | ENDER_PEARL | `actback` → 执行 `/back` |
| `home` | 21 | RED_BED | `acthome` → 打开家列表 |
| `warp` | 23 | ENDER_EYE | `actwarp` → 打开传送点列表 |
| `trashbin` | 25 | CAULDRON | `acttrashbin` → 打开垃圾桶 |
| `dominion` | 29 | GRASS_BLOCK | `cmd:dom`（执行 Dominion 的 `/dom`） |
| `tpa` | 31 | COMPASS | `acttpa` → 打开「选择传送目标」界面 |
| `chair` | 33 | OAK_SIGN | 无绑定（**纯说明按钮**，靠 lore 介绍告示牌椅子） |
| `ui-toggle` | 34 | BOOK | `acttoggleui` → 切换界面样式（dialogUI / 箱子） |

- 绑定关系来自 `menu.yml` 的 `bind.*-button`（默认值就是按钮 id）。
- 动作分派在 `MenuListener#onInventoryClick`：`cmd:` 前缀 → `closeInventory()` +
  下一 tick `performCommand(...)`；内部动作 → 各自的 `handleXxx`。
- **垃圾桶入口只有一个**：早期用烈焰棒做的 `clean` 按钮和垃圾桶功能重复，已删除。
- 菜单/sub-GUI 的点击一律 `setCancelled(true)`（含拖拽），防止物品被拿走。

**界面样式（dialogUI / 箱子）**：玩家偏好存 `settings.db`（`ui_preferences` 表，默认 dialogUI），
用 `/menuui` 命令或菜单里的「界面样式」按钮切换。`MenuCommand` 按偏好分流：

- dialogUI → `ui/DialogMenu`（Paper 弹窗：`DialogType.multiAction(...).columns(2)`，
  每按钮一个 `customClick` 回调直接执行动作）
- box → `MenuManager.openMainMenu`（箱子）

两种界面**共用** `MenuManager.activeButtons / resolveAction / runAction / canOpen`，
因此按钮内容与点击结果完全一致；`MenuListener` 已经薄化成「关界面 → `runAction`」。

**列表类界面也已 dialogUI 化**（1.3/1.4/1.5）：
- `ui/DialogTeleportList` —— 家列表 / 传送点列表共用的弹窗版（一页 6 条 + 上一页/返回/下一页翻页，翻页重开弹窗），点击条目传送
- `ui/DialogTpaTarget` —— 在线玩家选择弹窗版，点击发起 `/tpa`
- 二者与箱子 UI 一样按偏好分流（`MenuManager.openHomeList / openWarpList / openTpaTarget`）
- **dialogUI 按钮无左右键**，列表只保留「点击 = 传送 / 发起 tpa」主动作；删除用 `/delhome /delwarp`、`tpahere` 用 `/tpahere`（箱子 UI 保留右键）

垃圾桶（需要取物品）保持箱子 UI，不做 dialogUI。

---

## 2. 传送系统（teleport 包）

数据存 SQLite（`teleport.db`），会话态（tpa）存内存。详见 §8。

### 2.1 传送内核 `TeleportManager#teleport(player, target)`

所有传送（back/home/warp/tpa/GUI 点击 / dialogUI 列表）都走这里，保证行为一致：

```
1) target == null / !worldExists()   → false（世界不存在/未加载）
2) safe-location: true → findSafeLocation()
     要求：脚下是实心方块 + 身位与头部可通行；从原坐标上下交替搜索，最多 safe-search-distance 格
3) delay-seconds <= 0 → executeTeleport() 立即执行
   否则 → startDelayedTeleport()（见下）
4) executeTeleport：
     back.enabled && back.record-teleport → 记录「传送前位置」为 /back 目标
     markInternalTeleport()（1 秒窗口，见 2.2）
     player.teleport(dest)
     播放 teleport.sounds.teleport（默认 ENTITY_ENDERMAN_TELEPORT）
```

**延迟传送（delay-seconds 默认 3 秒）**：`startDelayedTeleport()` 逐秒执行 ——
- 开始时：提示 {seconds} + 播放 `teleport.sounds.start`（BLOCK_NOTE_BLOCK_PLING）
- 每秒：脚下刷 PORTAL 粒子（`particle-interval-ticks` 控制频率）+ 播放 `teleport.sounds.tick`（BLOCK_NOTE_BLOCK_HAT）
- 最后 1 秒：异步 `getChunkAtAsync` 预加载目标那**一个**区块（不加 ticket，传完自然卸载）
- 到点：二次校验世界仍存在 → executeTeleport

**延迟传送的打断条件**（`TeleportListener` / `TeleportManager`）：
- 玩家**跨越方块坐标**（同一方块内的转头/微位移不算）
- 玩家**受到任何伤害**（`EntityDamageEvent` → `cancelOnDamage`，提示区别于移动打断）
- 任何**非本插件**发起的传送（`PlayerTeleportEvent` 且 cause≠PLUGIN 或不在内部标记窗口内）
- 玩家退出、插件卸载时取消全部等待任务

**冷却**：`getCooldownRemaining() / applyTeleportCooldown()` 由**调用方**决定是否使用。
`/back` 不检查也不施加冷却；home/warp/tpa 各自有冷却。

**传送到家/传送点的统一入口**：`teleportHome(player, name)` / `teleportWarp(player, name)`
把「存在性 / 世界 / 冷却 / 传送 / 提示」的完整逻辑抽到 TeleportManager，
命令、箱子 GUI、dialogUI 三处只调这一个方法，保证行为一致。

### 2.2 `/back`（`BackCommand`）

```
权限 sfpmenu.teleport → back.enabled → isStorageAvailable()
→ 从 last_locations 读该玩家记录 → worldExists() 校验
→ manager.teleport(player, target) → back.success
```

**记录时机**（`TeleportListener` + `TeleportManager`，各由 teleport.yml 的 `back.*` 控制）：

| 时机 | 触发点 | 配置键 | 默认 |
|---|---|---|---|
| 死亡 | `PlayerDeathEvent` | `record-death` | true |
| 传送前 | `executeTeleport()` 内记录（本插件传送）；非本插件传送由 `onTeleport` 记 `from` | `record-teleport` | true |
| 切换世界 | `PlayerTeleportEvent` 比较 from/to 世界名 | `record-world-change` | true |
| 退出 | `PlayerQuitEvent` | `record-quit` | false |

**关键设计**：`onTeleport` 里先判断 `internal`（`cause == PLUGIN` 且 1 秒内被 `markInternalTeleport()`
标记过）；是则**直接 return 不记录** —— 因为 `executeTeleport()` 已经记了正确的位置，
再记一次会用「传送后的目标位置」把它覆盖掉。跨世界也不再监听 `PlayerChangedWorldEvent`
（那个事件拿不到旧坐标，且会在本插件自身传送后触发）。

**只存一层**：`last_locations` 是 `ON CONFLICT(player_uuid) DO UPDATE`，每人一行覆盖写，
不是历史栈。

### 2.3 `/home` 系列（`HomeCommand`）

四个标签共用一个类，`action ∈ {home, sethome, delhome, homes}`。
统一前置：玩家 → `sfpmenu.teleport` → `home.enabled` → `isStorageAvailable()`。

| 分支 | 流程 |
|---|---|
| `home` 无参 | `HomeListGui.open(...)` 打开家列表 |
| `home <名>` | 取名 → 不存在提示 → `worldExists()` → 冷却(`home.teleport-cooldown-seconds`) → `teleport()` → 施加冷却 + 提示 |
| `sethome [名]` | 设置冷却(`set-cooldown-seconds`) → 名字校验（`util/NameUtil`：**中文**/字母/数字/下划线，1~16）→ **若是新家**校验上限（`max-homes`，`sfpmenu.home.bypass-limit` 可绕）→ `HomeStore#save` → 施加设置冷却 + 提示 |
| `delhome <名>` | `HomeStore#delete`，返回 false 说明不存在 |
| `homes` | 逐条列出名字 + 坐标 |

补全：仅 `home` / `delhome` 的第一个参数补全家名。

### 2.4 `/warp` 系列（`WarpCommand`）

结构同 home，`action ∈ {warp, setwarp, delwarp, warps}`。
统一前置：玩家 → `sfpmenu.teleport` → `warp.enabled` → `isStorageAvailable()`。

- `setwarp` / `delwarp` **额外**校验 `warp.set-permission` / `warp.delete-permission`
  （teleport.yml 可配置；留空表示人人可用）。
- 传送流程：存在 → `worldExists()` → 冷却(`warp.teleport-cooldown-seconds`) → `teleport()`。
- 传送点全服共享，`warps` 表主键是 `warp_name`。

### 2.5 `/tpa` `/tpahere` `/tpaccept` `/tpdeny` `/tpaui`（`TpaManager` + `TpaCommand`）

**请求会话态不入库**（重启即失效；进库反而要处理过期清理）；
**界面偏好入库**（`settings.db` 的 `tpa_ui_preferences`，见 §8）。

`TpaCommand`（五个标签一个类，`action` 区分）统一前置：
玩家 → `sfpmenu.teleport` → `tpa.enabled`。

**发起**（`/tpa 名字` = 我去他那 / `/tpahere 名字` = 让他来我这）：
```
request(from, target, type)
  1) 自己 → 拒绝
  2) 发起冷却（tpa.request-cooldown-seconds）
  3) 该接收者已有请求 → 移除旧的（取消其过期任务）并通知被取代的发起者
  4) 登记 pending[target] = (from, fromName, targetName, type, expireAt, task)
     并 runTaskLater(expire-seconds)：到点若仍在 → 移除 + 通知双方已过期
  5) deliver(target, ...)：按**接收者自己**的界面偏好二选一
       DIALOG → 弹窗（multiAction：接受 / 拒绝 / 切换界面）+ 一行「手打命令」兜底
       TUI    → 聊天里带可点 [接受]/[拒绝]/[切换为弹窗] 的消息 + 手打提示
  6) 给发起者回执
```

**回应界面形式可切换（偏好存数据库）**：`/tpaui` 命令，或直接点界面里的
「切换界面」（弹窗）/「[切换为弹窗]」（聊天）按钮 —— 三者都走 `TpaManager#switchUi`：
切换 `tpa_ui_preferences`（`dialog` / `tui`，默认 `dialog`），并**立即用新形式重发**
当前待处理的那笔请求。该偏好**独立于**主菜单的 dialogUI/箱子偏好（`ui_preferences`）。

**兜底**：Dialog 是较新的客户端能力，服务器装了 ViaVersion/ViaBackwards/ViaRewind，
低版本客户端可能渲染不出弹窗，所以弹窗形式下也附一行「手打命令」提示；
任何形式都能用 `/tpaccept` `/tpdeny` 响应 —— **所有按钮最终都执行这两个命令**
（弹窗按钮是 `DialogAction.staticAction(ClickEvent.runCommand("/tpaccept 名字"))`）。

**接受**（`accept`）：
```
取出并移除 pending（名字对不上则提示；没有则提示）
→ 发起者离线？提示并结束
→ 双方提示
→ 走 TeleportManager#teleport：
     TO   → 把 from 传到 target 当前位置（冷却记在 from 身上）
     HERE → 把 target 传到 from 当前位置（冷却记在 target 身上）
→ 失败回 teleport.failed
```
**拒绝**：取出并移除 + 双方提示。

**清理**（`TpaListener` → `onQuit`）：玩家退出时，清掉①他作为接收者的请求（通知发起者）
②他作为发起者的请求（通知接收者）。`TpaManager#shutdown` 在插件卸载时取消全部过期任务。

### 2.6 传送相关界面（gui 包）

`TeleportGuiListener` 统一处理三个界面的点击（先判 `TpaTargetHolder`，再判 `TeleportListHolder`）。

**列表界面**（`TeleportListGui`，家 / 传送点共用；`HomeListGui` / `WarpListGui` 是薄封装）：

- 54 格：内容 0~44，导航行 45~53 = `45 上一页(SPECTRAL_ARROW)` / `48 返回主菜单(OAK_DOOR)` /
  `49 信息纸(PAPER)` / `53 下一页(SPECTRAL_ARROW)`，其余灰板；到首页/末页时把翻页键换成灰板+「首页/末页」
- 左键条目 = 传送；右键条目 = 删除（家直接删，传送点需 `warp.delete` 权限）
- 删除后刷新列表并**回到首页**（不保留原页码）
- 槽位常量在 `TeleportListHolder`：`SLOT_PREV/SLOT_BACK/SLOT_INFO/SLOT_NEXT`

**玩家选择界面**（`TpaTargetGui` + `TpaTargetHolder`）：

- 内容区是**在线玩家的头颅**（不含自己），lore 显示所在世界
- 左键 = `/tpa 他`（我去他那）；右键 = `/tpahere 他`（他来我这）
- 发起成功后关闭界面，等对方在弹窗/聊天里响应
- 目标离线时提示并刷新列表

「返回主菜单」统一走 `MenuManager.openMainMenu()`（内含开关 + 打开权限校验，与 `/menu` 共用），
点击时先 `closeInventory()` 再 `runTask` 延迟一 tick 打开。

---

## 3. 自动扫地（clean 包）

**入口**：无命令，纯定时任务。`FloorCleanManager` 构造时启动 `runTaskTimer(20L, 20L)`（每秒 tick）。

```
每秒：
  1) 若 remainingSeconds 命中 warn-seconds（如 60/30/10）且未提醒过
       → clean.broadcast.reminders 为 true 时广播 clean.reminder（{seconds}）
  2) remainingSeconds--
  3) <= 0 → performClean()，然后重置为 cycleSeconds 并清空已提醒集合
```

`performClean()`：
```
遍历 Bukkit.getWorlds()
  ├─ config.isWorldIncluded(名)：黑名单优先，白名单非空则只扫白名单内的世界
  ├─ 遍历世界内实体，只处理 Item（掉落物）
  │    ├─ skip-named-items 且物品有自定义显示名 → 跳过（不收集，但实体照样 remove）
  │    └─ 否则收进 collected
  │    └─ entity.remove()
  └─ to-trashbin && 垃圾桶可用 → trashBinManager.clear()（清掉上一轮）+ addAll(collected) + save()
                                  否则收集物直接丢弃（不可恢复）
→ 控制台日志 + broadcast.result 为 true 时广播 clean.result（带 [打开垃圾桶] 可点击按钮）
```

- 周期由 `clean.yml` 的 `cycle-seconds` 决定，**代码强制最低 60 秒**。
- PAPI 占位符：`CleanTimePlaceholder`（identifier `stf`）暴露
  `%stf_cleantime%`（带颜色，阈值来自 `clean.placeholder.*`）与 `%stf_cleantime_plain%`。
  注册前提：PlaceholderAPI 已安装且扫地模块已启用。
- **垃圾桶只在「至下次扫地前」有效**：每轮清扫都会先 `clear()` 再收入本轮物品。

---

## 4. 垃圾桶（trashbin 包）

**入口**：`/trashbin`、菜单 `trashbin` 按钮。

```
TrashBinCommand#execute：玩家 → sfpmenu.player → manager 是否存在 → openPage(0)
TrashBinCommand.openPage(plugin, player, manager, page)（供命令与翻页共用）
  ├─ 页码夹紧 → TrashBinHolder(page, pageSize)
  ├─ 54 格标题含 {page}/{pages}
  ├─ 内容格 0~44：manager.getPage(page) 的 ItemStack 克隆
  ├─ 导航行同 §2.6（常量在 TrashBinHolder）
  └─ 打开界面 + 播放 trashbin.sounds.open
```

点击处理 `TrashBinListener`：
- 翻页 / 返回主菜单（同 §2.6）/ 信息纸无操作 / 导航区灰板忽略
- **点击物品格 = 取回**：`toGlobalIndex(page, slot)` → `takeItem(index)` →
  `player.getInventory().addItem()`，背包满则掉在脚下 → `save-immediately` 时立即写盘 →
  播放 `take` 音效 → 刷新当前页（若该页因取空而越界则回退一页）
- 所有点击与拖拽一律 cancel（**不能放入物品**）

数据：内存 `List<ItemStack>`，`trashbin-data.yml` 持久化（ItemStack 可直接序列化）。
`max-items` 超出时 `trim()` 丢弃**最旧**的（从下标 0 开始删）。
`page-size` 代码夹在 1~45。

---

## 5. 椅子（chair 包）

**入口**：玩家在「左右各一块木质告示牌、第一行分别是配置关键字」的方块上右键。

**椅子判定**（`ChairManager`）：
```
isChairBlock(block) → getChairFacingYaw(block) != null
  findLeftSignFace：四个水平方向找相邻方块
     ├─ 是允许木种的告示牌（材质以 allowed-woods 里某个前缀开头，且以 _SIGN/_WALL_SIGN/
     │   _HANGING_SIGN/_WALL_HANGING_SIGN 结尾）
     ├─ 第一行匹配 left-keyword
     └─ 对面方块是同类告示牌且第一行匹配 right-keyword
  → 由左侧告示牌朝向推出玩家应朝的方向（getFacingFromLeft + getYawFromFacing）
```

**坐下**（`ChairListener#onPlayerInteract`，仅主手 RIGHT_CLICK_BLOCK）：
1. 已坐着且点的是**当前座位**且 `stand-on.reclick` → 起身
2. `isChairBlock` 判定通过：
   - `isBlockInUse` → 提示 `chair.occupied` 并取消事件
   - 否则取消事件（阻止放置/交互）→ `sitPlayer()`

`sitPlayer()`：按方块实际高度算坐姿（`seat-height-offset`）→ 生成**隐形小盔甲架**
（`setGravity(false) / setVisible(false) / setInvulnerable(true) / setCustomName("stf-chair")`）
→ `addPassenger(player)` → 记入 `sittingPlayers` / `chairBlocks` → 提示 `chair.sit`。

**起身**（`standPlayer()` 移除盔甲架并清理两张表）触发点：

| 触发 | 事件 | 配置 |
|---|---|---|
| 潜行 | `EntityDismountEvent`（潜行会自动从乘客位下来） | — （无条件） |
| 再次右键同一座位 | `PlayerInteractEvent` | `stand-on.reclick` |
| 破坏椅子或相邻方块 | `BlockBreakEvent`（椅子方块或其相邻 1 格） | `stand-on.break` |
| 传送离开 | `PlayerTeleportEvent` | `stand-on.teleport` |
| 退出游戏 | `PlayerQuitEvent` | — |

另外 `onPlayerKick` 会**取消**「flying / 飞行 / moving too fast / 移动过快」这几种踢出，
避免原版反作弊把坐在盔甲架上的玩家误踢。

---

## 6. 假人（bot 包）

**入口**：`/bot create|remove|list|removeall`（`sfpmenu.bot`，默认 op）。

**假人是真正的玩家实体**（NMS `ServerPlayer`），不是盔甲架之类的近似物：
它会出现在在线玩家列表里（`/list` 可见）、计入玩家数，并**天然保持周围区块加载**
（实体本身不会保区块，只有玩家或区块 ticket 才会）。

`NmsBotFactory#spawn`（全反射，Paper 无公开 API）：
```
ServerPlayer(server, level, GameProfile(uuid,name), ClientInformation.createDefault())
Connection(PacketFlow.SERVERBOUND)          ← 本地假连接
  └─ Connection.channel（public 字段）= netty EmbeddedChannel    ← 见下
ServerGamePacketListenerImpl(server, connection, player, cookie)
CommonListenerCookie.createInitial(profile, false)
ServerPlayer.connection（public 字段）= listener
PlayerList#placeNewPlayer(connection, player, cookie)            ← 服务端自己的加入流程
```
- `EmbeddedChannel` 是「吞包黑洞」：`placeNewPlayer` 会往连接写登录包，没有 channel 会 NPE。
  它没人消费的 outbound 队列由 `BotManager` 的定时任务（每 60 秒）清空，避免堆积。
- 皮肤：从创建者 `CraftPlayer#getProfile()` 复制 `textures` 属性；`bot.yml` 的 `skin-source`
  可改为指定玩家名或 `none`（默认皮肤）。
- UUID 由名字派生（`nameUUIDFromBytes("StarfallBot:" + 名字小写)`），所以**名字即身份**，重名直接拒绝。
- 名字规则 `[A-Za-z0-9_]{1,16}`；`max-per-player` 限制每位玩家的数量（0=不限，控制台不计入）。

`BotManager`：
- 记录写入 `bots.yml`（name/uuid/owner/world/x/y/z/yaw/pitch）
- `setupBot()` 里 **`runTask` 延后一 tick** 调 `restoreOnStart()` 重建（避开插件启用栈，
  此时世界已加载完毕）；单个失败只记日志
- `shutdown()` 停掉清理任务；`saveData()` 在创建/删除/禁用时写盘

**移除**：反射调 `PlayerList#remove(ServerPlayer)`，再 `Player#remove()` 兜底。
**不能用 `Bukkit#kick()`** —— 它依赖向客户端发断开包，假连接发不出去，实测移除不掉。

---

## 7. 信息显示（display 包）

两块**纯展示**模块，内容全部由配置文件驱动（`tab.yml` / `scoreboard.yml`），
支持 MiniMessage + 内置占位符 + PlaceholderAPI 占位符，按 `refresh-ticks`（默认 20 = 每秒）刷新。

**内置占位符**（`display/DisplayText`，不依赖任何插件）：
`{player}` `{world}` `{x}` `{y}` `{z}` `{online}` `{max}` `{tps}` `{mspt}`
（`{tps}` = 最近 1 分钟 TPS、`{mspt}` = 每 tick 平均耗时，均由插件自己算**当前值**，
比 spark 的 `%spark_tps%`（一次给多个时间窗口）更适合「只看当前」的场景）

**渲染顺序（踩过坑，别改）**：
1. 先替换内置占位符
2. 把模板交给 MiniMessage 解析成 Component —— 模板自身的标签结构因此一定正确
3. 最后在**组件上**用 `Component#replaceText` 做 PAPI 替换，返回值作为纯组件插入

如果反过来（先把 PAPI 替换进字符串、再整体 MiniMessage 解析），像 spark 这类
**返回值自带颜色码（含 `§r`）**的扩展会把外层标签重置掉，`</white></gray>`
就会以字面形式显示出来。PAPI 用**反射**调用（可选依赖，直接引用会在未装 PAPI 时类加载失败），
返回值里的 `§` 色码交给 `LegacyComponentSerializer` 解析。

### 7.1 Tab 列表（`display/TabManager`）

- `tab.yml` 的 `header` / `footer` 各是多行列表 → `Audience#sendPlayerListHeaderAndFooter`
  （注意：Paper 的 `Player` 上没有 `setPlayerListHeaderFooter`，那是旧 API）
- 每秒为所有在线玩家刷新一次

### 7.2 计分板（`display/ScoreboardManager`）

- `scoreboard.yml` 的 `title` + `lines`（最多 15 行，超出会告警并截断；`""` 表示空行）
- 每位玩家一个**独立** `Scoreboard`（`Bukkit.getScoreboardManager().getNewScoreboard()`），
  不污染服务端主计分板；行内容用 `Score#customName(Component)` 承载（entry 只是内部 key，不显示），
  并用 `Objective#numberFormat(NumberFormat.blank())` 隐藏行尾的分数数字
- 玩家退出时丢弃其计分板实例（`PlayerQuitEvent`）
- ⚠️ 其他插件若也占用右侧计分板会互相覆盖，建议只留一个

### 7.3 音效解析（`util/SoundUtil`）

配置里既支持 Bukkit 枚举名（`ENTITY_ENDERMAN_TELEPORT`）也支持注册名
（`minecraft:entity.enderman.teleport`）。查找走 `Registry.SOUNDS` + 首次构建的名称缓存 ——
**`Sound#valueOf` 已被标记为待删除，不要再用**；同理 `Nameable#setCustomName(String)`、
`Sign#getLine(int)`、`PlayerKickEvent#getReason()` 在 2.6.0 也都换成了 Adventure 版本
（`customName(Component)`、`Sign#line(int)`、`reason()`）。

## 8. 数据与持久化

| 存储 | 文件 | 存放内容 | 代码 |
|---|---|---|---|
| SQLite | `teleport.db` | `homes` / `warps` / `last_locations` | `Database` + 三个 Store |
| SQLite | `settings.db` | `ui_preferences`（界面样式偏好）、`tpa_ui_preferences`（传送回应界面偏好） | `UiPreferenceStore` |
| YAML | `trashbin-data.yml` | 垃圾桶物品列表 | `TrashBinManager` |
| YAML | `bots.yml` | 假人记录（用于重启重建） | `BotManager` |
| 内存 | — | tpa 请求、传送冷却、延迟传送队列、在座玩家 | 各 Manager |

### 8.1 传送数据库分层

```
TeleportManager
  ├─ Database      连接（单连接 + synchronized getConnection/commit/close）、PRAGMA(WAL/外键)、
  │                表结构初始化与迁移
  ├─ HomeStore     homes：save / get / delete / listNames / count
  ├─ WarpStore     warps：save / get / delete / listNames
  └─ BackStore     last_locations：save / get
        └─ LocationRow（包内工具）：结果集一行 → StoredLocation
```

- `StoredLocation` 是 record（world/x/y/z/yaw/pitch），**存世界名而不是 World 引用**，
  跨重启安全；世界被删时 `toLocation()` 返回 null，调用方提示 `teleport.world-missing`。
- 全部走 `PreparedStatement`，无注入面。
- **数据库不可用时静默降级**：查询返回 null/空，写入返回 false，
  上层用 `TeleportManager#isStorageAvailable()` 判断并提示玩家。
- 主键：`homes(player_uuid, home_name)` / `warps(warp_name)` / `last_locations(player_uuid)`，
  现有查询都被索引覆盖。
- **单连接 + 同步访问，只允许在主线程使用**：`getConnection()` 本身同步，但返回的连接在锁外使用。
  当前全部调用都在主线程（命令 / 事件 / GUI / 同步调度任务）。
  **若以后改成异步保存，必须给数据访问层补同步。**

### 8.2 表结构迁移（改表结构必看）

`Database` 里有 `SCHEMA_VERSION`，存在 SQLite 的 `PRAGMA user_version`。
老库与新库的 `user_version` 都是 0，所以从 0 逐级判断能同时覆盖两种情况。

**改表结构的两步**：
1. `SCHEMA_VERSION` +1
2. 在 `migrate(int from)` 里追加 `if (from < N) { ... }` 分支

可用工具：`execute(...)`、`tableExists()`、`columnsOf()`（`PRAGMA table_info`）、
`addColumnIfMissing(表, 列, 定义)`（幂等加列）。
每一步都必须能重复执行（建表用 `IF NOT EXISTS`、加列用 `addColumnIfMissing`）。
库版本高于代码时**只告警、不做任何改动**。

SQLite 的 `ALTER TABLE` 限制：只能加列 / 改列名 / 删列（3.35+），
**不能**加「无默认值的 NOT NULL 列」，也不能改列类型或主键。

### 8.3 备份建议

正常停服后 `-wal` 会自动合并回 `teleport.db`，**直接拷 `teleport.db` 即可**；
开服状态下想拷贝，先执行 `/sfp db checkpoint`。

---

## 9. 改动时的注意事项（踩坑清单）

1. **改 `resources/*.yml` 里已存在键的默认值 → 必须手工同步服务器上的同名配置**。
   `mergeDefaults()` 只补缺失键、不覆盖已有键。**删按钮/删键也一样要手工删线上的**。
   （新增键会自动补，但注意：`mergeDefaults()` 的 `save()` 只写磁盘、不改内存，
   所以它在 `save()` 之后重新 `loadConfiguration` 了一次 —— 否则首次升级时新键的叶子字段
   在内存里读不到，比如新按钮 `slot` 读成 -1。）
2. **配置类的 `onLoaded()` 不得依赖子类字段初始化器**：`AbstractConfig` 的构造器就会调用
   `load()` → `onLoaded()`，而 Java 是在 `super(...)` 返回**之后**才执行子类字段初始化器 ——
   那一刻集合字段还是 null（NPE），而且初始化器随后还会把填好的内容覆盖成空集合。
   集合类字段一律在 `onLoaded()` 内部 `new`。（2.0.3 修的启动崩溃就是这个）
3. **改表结构必须走 §8.2 的两步**，否则老库不会升级。
4. **新增菜单按钮**：只需改 `menu.yml`（`buttons.<id>` + 必要时 `bind.*-button`）。
   若要内部动作，才需要动 `MenuHolder`（加常量）+ `MenuManager`（`resolveAction` /
   `isFeatureEnabled`）+ `MenuListener`（分派）。`command:` 留空且不在 `bind.*` 里 = 点了没反应
   （`/sfp test` 会报出来；有 lore 的纯说明按钮会被识别为「仅作说明展示」）。
5. **子界面导航行布局改动要同步两处**：`TrashBinHolder` 与 `TeleportListHolder` /
   `TpaTargetHolder` 各有一份 `SLOT_*` 常量。
6. **开关类改动（enabled）无法热重载**：监听器注册不可逆，必须重启服务器；
   `/sfp reload` 只能重载参数。
7. **换 jar 前先确认服务端没在跑**（`jps -l | grep server.jar`）：
   运行中的服务端会锁住旧 jar（`mv` 报 `Device or resource busy`），
   而 `cp` 可能已经成功 → plugins 下出现两个同名插件的 jar，重启即冲突。
   正确顺序：停服 → 旧 jar 改名 `xxx.jar.bak`（Paper 只扫 `*.jar`）→ 拷新 jar → 启动。
8. **NMS 反射（假人）随 Minecraft 版本可能失效**：签名先用服务端 jar 反射核对
   （`versions/<ver>/paper-*.jar` + `libraries/**`），失败时看日志里的「假人重建失败」异常。
9. `paper-plugin.yml` 的 `api-version` 只有**大版本**（如 `'26.1'`），
   改动它的语义是「放弃对更老版本的兼容」。

---

## 10. 已知未生效 / 预留的配置项

以下五项曾在代码中「定义了但没人用」，**2.5.0 已全部删除**（配置键 + 代码访问器 + 引用一并清理）：

| 配置项 | 原状态 |
|---|---|
| `teleport.allow-cross-world` | 内核条件被六个传 `true` 的调用点短路，永不生效（删除后 `teleport()` 签名也去掉了 `crossWorldAllowed` 参数） |
| `chair.stand-on.sneak` | 无引用；潜行起身实为 `EntityDismountEvent` 无条件处理 |
| `clean.worlds.only-loaded-chunks` | 无引用 |
| `trashbin.global` | 无引用；按玩家隔离未实现 |
| `config.yml` 的 `currency-name` | `GlobalConfig#getCurrencyName()` 无调用点，扫地广播的 `{currency}` 占位符也是死代码 |

## 11. 新增功能检查清单

- [ ] `resources/` 加 `xxx.yml`（首字段 `enabled`，注释写清每个键）
- [ ] `config/module/XxxConfig.java` 继承 `AbstractConfig`，`onLoaded()` 里读值
      （集合字段务必在 `onLoaded()` 内 `new`）
- [ ] `ConfigManager`：加字段 + 访问器 + `loadAll()` 实例化 + `describeState()` 补一行
- [ ] 功能类构造器接收 `ConfigManager`，内部取 `configManager.xxx()`
- [ ] `SfpMain`：加字段、`getXxxManager()`、`setupXxx()`（按 `isEnabled()` 决定是否注册）
- [ ] 文案写进 `messages.yml`，代码用 `messages.raw/send`（不要硬编码中文到代码里，
      诊断类输出除外）
- [ ] 若要命令：写 `XxxCommand implements BasicCommand`，在 `registerCommands()` 里注册，
      权限节点补进 `paper-plugin.yml`
- [ ] 若要界面：新建 Holder + Gui，点击统一挂在对应 Listener；导航行沿用 §2.6 的槽位约定
- [ ] 数据持久化：能进 SQLite 的走 db 包（注意 §8.2 的迁移步骤）；
      小数据用 YAML；**纯会话态一律放内存**
- [ ] 若绑定菜单按钮：`menu.yml` 加按钮 + `bind.*-button`，并按第 4 条改三个类
- [ ] **把本功能写进 features.md**（入口 / 调用链 / 流程 / 配置键 / 权限 / 数据文件）
- [ ] 构建 + 真机冒烟（`/sfp test` 应全绿；GUI 与交互需真人验证）
- [ ] 版本号：Bug 修复 `z+1`、小内容更新 `y+1`，没有大更新不要动 `x`
