Set-Location "D:\document\Projects\df-item-crafting-monitoring"

Write-Output "=== 当前项目功能盘点（对照 spec 12.1~12.5 + 15.1~15.5）==="
Write-Output ""

# 1. 授权方式
Write-Output "[1] 授权方式"
$src = Get-ChildItem -Path app\src\main\java -Recurse -Filter "*.kt" -ErrorAction SilentlyContinue | Get-Content -Raw
if ($src -match "WebView|webview") { Write-Output "  - WebView 登录: YES" } else { Write-Output "  - WebView 登录: NO" }
if ($src -match "OAuth|oauth") { Write-Output "  - QQ/微信 OAuth: 提及" } else { Write-Output "  - QQ/微信 OAuth: 无" }
Write-Output ""

# 2. 数据拉取
Write-Output "[2] 数据拉取"
if ($src -match "CraftingRepository|fetchCrafting") { Write-Output "  - 特勤处接口调用: YES" } else { Write-Output "  - 特勤处接口调用: NO" }
Write-Output ""

# 3. 写操作检查（spec 5.2 只读访问）
Write-Output "[3] 写操作检查（spec 要求只读）"
$writeOps = Select-String -Path app\src\main\java -Pattern "POST |PUT |DELETE |fetchCrafting|buildQqServerSide|write\(|submitQ" -ErrorAction SilentlyContinue
if ($writeOps) {
    Write-Output "  - 网络层找到的可能写操作："
    $writeOps | Select-Object -First 5 | ForEach-Object { Write-Output "    $($_.Filename):$($_.LineNumber): $($_.Line.Trim())" }
} else { Write-Output "  - 写操作: 无" }
Write-Output ""

# 4. WorkManager
Write-Output "[4] 后台轮询 (spec 7.3 / 9 / 12.2)"
$buildGradle = Get-Content app\build.gradle -Raw
if ($buildGradle -match "workmanager") { Write-Output "  - WorkManager 依赖: YES" } else { Write-Output "  - WorkManager 依赖: NO" }
if ($src -match "WorkManager|PeriodicWorkRequest|CoroutineWorker") { Write-Output "  - WorkManager 使用: YES" } else { Write-Output "  - WorkManager 使用: NO" }
Write-Output ""

# 5. 桌面卡片
Write-Output "[5] 桌面卡片 (spec 7.5 / 12.3)"
if (Test-Path app\src\main\java\com\local\dfcraftmonitor\widget) { Write-Output "  - widget 目录: YES" } else { Write-Output "  - widget 目录: NO" }
if ($src -match "AppWidget|Glance") { Write-Output "  - AppWidget/Glance 引用: YES" } else { Write-Output "  - AppWidget/Glance 引用: NO" }
Write-Output ""

# 6. 设置页
Write-Output "[6] 设置页 (spec 7.7 / 15.4)"
if ($src -match "SettingScreen|SettingsScreen") { Write-Output "  - SettingScreen: YES" } else { Write-Output "  - SettingScreen: NO" }
Write-Output ""

# 7. 退出登录 / 暂停 / 清除
Write-Output "[7] 用户控制（spec 7.7 / 15.4：暂停/退出登录/清除数据）"
if ($src -match "logout|signOut|sessionHolder.clear|暂停") { Write-Output "  - 退出登录/暂停: 提及" } else { Write-Output "  - 退出登录/暂停: 无" }
if ($src -match "DataStore|EncryptedSharedPreferences|Tink") { Write-Output "  - 凭证加密存储: YES" } else { Write-Output "  - 凭证存储: 仅内存（SessionHolder）" }
Write-Output ""

# 8. 异常处理
Write-Output "[8] 异常处理与降级 (spec 11 / 12.4)"
if ($src -match "Result\.failure|UiState\.Error|onFailure") { Write-Output "  - Result/Error 状态: YES" } else { Write-Output "  - Result/Error: NO" }
if ($src -match "try.*catch.*Exception|runCatching") { Write-Output "  - try/catch 异常捕获: YES" } else { Write-Output "  - try/catch: NO" }
Write-Output ""

# 9. 单测
Write-Output "[9] 单测覆盖（spec 13）"
$testCount = (Get-ChildItem -Path app\src\test -Recurse -Filter "*.kt" -ErrorAction SilentlyContinue | Measure-Object).Count
Write-Output "  - app/src/test Kotlin 测试数: $testCount (0=NO, >0=YES)"
Write-Output ""

# 10. Manifest 关键项
Write-Output "[10] Manifest 关键项"
$manifest = Get-Content app\src\main\AndroidManifest.xml -Raw
if ($manifest -match "POST_NOTIFICATIONS") { Write-Output "  - POST_NOTIFICATIONS 权限: YES" } else { Write-Output "  - POST_NOTIFICATIONS 权限: NO" }
if ($manifest -match "FOREGROUND_SERVICE") { Write-Output "  - FOREGROUND_SERVICE 权限: YES" } else { Write-Output "  - FOREGROUND_SERVICE 权限: NO" }
if ($manifest -match "RECEIVE_BOOT_COMPLETED") { Write-Output "  - 开机自启 RECEIVE_BOOT_COMPLETED: YES" } else { Write-Output "  - 开机自启: NO" }
