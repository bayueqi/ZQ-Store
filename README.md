# ZQ Store

一款基于GitHub的开源应用商店，帮助用户发现和下载各种平台的开源应用。

## 功能特性

- **多平台支持**：支持Windows、macOS、Linux、Android、iOS等平台的应用搜索和下载
- **智能分类**：按推荐、游戏、社交、视频、音乐、动漫等分类浏览应用
- **高级搜索**：支持关键词搜索和平台筛选
- **应用详情**：查看应用的详细信息、版本历史和下载统计
- **安装管理**：支持直接安装Android应用（APK）
- **收藏功能**：收藏喜欢的应用以便后续查看
- **更新提醒**：显示应用的更新时间和版本信息

## 支持的文件格式

- **Android**：APK、AAB
- **Windows**：EXE、MSI
- **macOS**：DMG、PKG
- **Linux**：DEB、RPM、AppImage
- **iOS**：IPA

## 如何使用

1. **浏览应用**：在首页查看推荐应用，或通过分类浏览不同类型的应用
2. **搜索应用**：使用顶部搜索框输入关键词搜索应用
3. **筛选平台**：点击平台筛选按钮选择特定平台的应用
4. **查看详情**：点击应用卡片查看详细信息
5. **下载应用**：点击下载按钮下载应用，Android应用支持直接安装
6. **收藏应用**：点击收藏按钮将应用添加到收藏列表
7. **管理设置**：在设置页面调整应用行为和查看收藏的应用

## 技术栈

- **开发语言**：Kotlin
- **架构**：MVVM
- **网络请求**：Retrofit + OkHttp
- **UI框架**：Jetpack Compose
- **数据存储**：Room Database
- **GitHub API**：用于搜索仓库和获取releases

## 构建与安装

### 前置条件

- Android Studio Arctic Fox或更高版本
- JDK 11或更高版本
- Android SDK API 26或更高版本

### 构建步骤

1. 克隆仓库：
   ```bash
   git clone https://github.com/bayueqi/zq-store.git
   ```

2. 在Android Studio中打开项目

3. 构建APK：
   ```bash
   ./gradlew assembleDebug
   ```

4. 安装到设备：
   ```bash
   ./gradlew installDebug
   ```

## 贡献指南

欢迎贡献代码、报告bug或提出新功能建议！

1. Fork本仓库
2. 创建功能分支
3. 提交更改
4. 推送到分支
5. 创建Pull Request

## 关于

- **作者**：八月琪
- **仓库**：[https://github.com/bayueqi/zq-store](https://github.com/bayueqi/zq-store)
- **版本**：1.0.1

