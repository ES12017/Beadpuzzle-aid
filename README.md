# 拼豆辅助（BeadPixel）

一款安卓拼豆（拼豆像素画）辅助工具：创建任意尺寸画布、用色卡/自定义调色盘绘制、
图片转像素画、导出图纸并统计豆量。

> ⚠️ 本项目仅供学习交流，严禁商用。使用本应用转换图片时，请仅使用您拥有合法权利或
> 已获授权的图片。

## 功能特性

- **画布**：1~1000 格任意宽高，支持常用尺寸预设（16×16 ~ 100×100）
- **调色盘**：内置 26 个主流品牌色卡（约 5000 色，数据来源见声明）；支持按 RGB 值
  自定义色号、命名、调色盘打包导出/导入、排序与激活切换
- **绘制**：画笔 / 橡皮 / 填充 / 取色 / 平移；双指缩放平移；色号悬浮在画布上快捷切换；
  豆量统计（每种色号使用格数与占比）
- **图片转像素**：按画布尺寸与当前调色盘色号转换，可选保持比例/拉伸/抖动；
  采用 CIEDE2000 感知色差匹配颜色，色彩还原好
- **导出**：PNG 导出（可选含网格线），保存到相册或系统文件
- **预览**：无网格纯净预览，支持双指缩放
- **设置**：浅色/深色主题、网格显示、色号显示模式、撤销步数、导出位置等

## 界面

黑色简约风格，绘画时无关面板可收起；底部工具行紧凑，顶部为常用操作按钮。
（运行后即可体验，详见应用内各页面。）

## 构建

环境要求：

- JDK 17
- Android SDK（compileSdk 35）
- Gradle 8.10（项目内使用 `gradle.bat` / `gradle` 命令，或用 Android Studio 打开）

命令行构建（推荐使用项目内置的 Gradle Wrapper，自动从国内镜像下载 Gradle 8.10）：

```bash
cd BeadPixel
./gradlew :app:assembleDebug      # 调试包（Windows 使用 gradlew.bat）
./gradlew :app:assembleRelease    # 发布包
```

或使用本机已安装的 Gradle 8.10：

```bash
gradle :app:assembleDebug
```

产物位于 `app/build/outputs/apk/{debug,release}/`。

> 注意：
> 1. 仓库**不包含**签名文件 `keystore.jks`（见 `.gitignore`）。正式发布前请自行生成
>    keystore，并修改 `app/build.gradle.kts` 中的签名配置（建议改用环境变量传入
>    store/alias 密码，避免明文）。
> 2. `local.properties` 中的 SDK 路径为个人本机配置，不随仓库分发。

## 项目结构

```
BeadPixel/
├── app/src/main/java/com/beadpixel/app/   # 应用源码（Kotlin）
│   ├── CanvasView.kt                      # 画布渲染与手势（离屏缓存 + 直接绘制）
│   ├── ImageConverter.kt                  # 图片转像素（CIEDE2000 + 3D 查找表）
│   ├── ProjectStore.kt                    # 项目保存/加载（二进制格式）
│   ├── PaletteStore.kt                    # 调色盘存储
│   └── ...                                # 各页面与工具类
├── app/src/main/res/                      # 资源（布局/图标/内置色卡等）
├── THIRD_PARTY_NOTICES.md                 # 第三方组件与数据声明
└── build.gradle.kts                       # 构建配置
```

## 开源协议

本项目以 [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)
（知识共享署名-非商业性使用-相同方式共享 4.0 国际）协议开源：允许学习、使用、
修改与分享，但**严禁商用**；衍生作品须采用相同许可并保留署名。
许可证全文见 [LICENSE](LICENSE)。

第三方组件与内置色卡数据的许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 致谢

- 内置色卡数据整理自 [HansBug/pindou-color-data](https://github.com/HansBug/pindou-color-data)
  与 [maxcleme/beadcolors](https://github.com/maxcleme/beadcolors)
- 部分矢量图标来自 [Material Design Icons](https://fonts.google.com/icons)（Apache License 2.0）