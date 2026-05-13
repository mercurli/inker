
# 设计

```css
:root {
  /* Colors */
  --bg-page: #ffffff;
  --bg-surface: #ffffff;
  --bg-soft: #f8fafc;
  --text-primary: #111827;
  --text-secondary: #6b7280;
  --text-muted: #9ca3af;
  --border-default: #e5e7eb;
  --border-soft: #eef1f4;
  --primary-500: #1d9bf0;
  --primary-600: #0284e6;
  --success-500: #16a34a;
  --danger-500: #ef4444;

  /* Radius */
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-pill: 999px;

  /* Shadow */
  --shadow-card: 0 1px 2px rgba(16, 24, 40, 0.06);
  --shadow-pop: 0 8px 24px rgba(16, 24, 40, 0.10);

  /* Spacing */
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 20px;
  --space-6: 24px;
  --space-8: 32px;

  /* Typography */
  --font-family-base: "Inter", "PingFang SC", "Microsoft YaHei", sans-serif;
  --font-size-xs: 12px;
  --font-size-sm: 13px;
  --font-size-md: 14px;
  --font-size-lg: 18px;
  --font-size-xl: 40px;
  --line-height-tight: 1.2;
  --line-height-base: 1.5;

  /* Components */
  --btn-height-sm: 30px;
  --btn-height-md: 36px;
  --btn-height-lg: 40px;
}
```

### 4. 核心组件规范

#### 4.1 左侧导航栏
- 宽度固定 `72px`，图标按钮使用圆角矩形，尺寸 `40x40`。
- 默认图标使用中灰色；选中项背景为主色，图标为白色。
- 导航项垂直间距 `12px`，顶部与底部保留 `16px` 安全间距。

#### 4.2 顶部工具栏
- 高度 `64px`，左右内边距 `20px`。
- 搜索框高度 `36px`，圆角 `999px`，边框 `1px solid var(--border-default)`。
- 右上角用户信息块与图标按钮高度与输入控件保持一致。

#### 4.3 统计总览区（大数字卡片）
- 主数值字体建议 `40px/700`，金额小数和单位使用较小字号并弱化颜色。
- 次级描述（如 last transaction）使用 `13px`，正向变化使用 `--primary-500` 或 `--success-500`。
- 主操作按钮组采用“次级按钮 + 文本按钮 + 主按钮”层级。

#### 4.4 通用卡片
- 卡片背景 `var(--bg-surface)`，圆角 `var(--radius-md)`，描边 `1px solid var(--border-soft)`。
- 内边距 `16px`，标题与内容间距 `12px`。
- 卡片标题 `14px/600`，辅助说明 `12px` 且颜色 `var(--text-muted)`。

#### 4.5 图表卡（Activities）
- 折线图线宽建议 `2px`，主线为低饱和蓝。
- 鼠标悬停点显示数据浮层，浮层圆角 `8px`，阴影 `var(--shadow-pop)`。
- X 轴刻度标签使用 `12px`，颜色 `var(--text-muted)`。

#### 4.6 交易列表卡（Recent Transaction）
- 列表行最小高度 `52px`，行间以浅分割线区分。
- 左侧头像/图标容器尺寸 `28x28`，名称与时间两行排布。
- 金额右对齐，收入为深色或绿色，支出为红色。

#### 4.7 钱包卡（Saving Wallet）
- 金额与“Total Saving”采用上下结构，金额为主视觉。
- 底部分类图标按钮保持统一尺寸（建议 `44x44`），图标下方可加简短标签。
- 同一卡片内按钮样式保持统一，避免混用不同圆角与阴影。

#### 4.8 按钮系统
- 主按钮：`background: var(--primary-500)`，白字，hover 使用 `--primary-600`。
- 次按钮：白底描边，文字 `--text-primary`。
- 文字按钮：无底色，hover 时出现 `--bg-soft` 背景。
- 按钮横向最小内边距 `12px`，图标与文字间距 `6px`。

### 5. 状态规范
- 默认态：静态展示，不额外强调边框与阴影。
- 悬停态（Hover）：
  - 卡片提升为 `--shadow-pop` 的弱化版本（建议不超过 `0 4px 12px`）。
  - 可点击列表行背景切换为 `--bg-soft`。
- 激活态（Active）：
  - 导航选中项显示主色背景。
  - 按钮按下时亮度降低约 `4%`。
- 禁用态（Disabled）：
  - 元素透明度建议 `0.45`。
  - 禁用点击与 pointer 事件，不可出现 hover 效果。

### 6. 响应式规则
- 桌面端（`>=1200px`）：保持 3 列布局。
- 平板端（`768px ~ 1199px`）：切换为 2 列布局，右侧交易列表下移。
- 移动端（`<768px`）：
  - 主内容区改为单列堆叠。
  - 左侧导航切换为底部 Tab 或抽屉菜单。
  - 顶部搜索可折叠为图标触发。
- 响应式下卡片内边距允许从 `16px` 降为 `12px`，但不得小于 `12px`。

### 7. 实现约束
- 前端样式实现优先使用 CSS 变量（Design Tokens）或 Tailwind Token 映射，禁止硬编码重复色值。
- 新增 Dashboard 页面必须复用统一卡片结构、按钮层级和标题字号，避免模块间视觉漂移。
- 图表、列表、金融数据卡必须遵循统一留白节奏（12/16/24）与标题区样式。
- 若业务页面存在特殊视觉需求，必须在 PR 描述中说明偏离原因，并给出对齐策略。
