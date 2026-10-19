# 知校园：校园知识库问答系统

本科毕业设计项目，面向山东青年政治学院公开规章、办事指南和申请表下载场景。管理员维护资料，学生提问后查看回答及原文来源。

> GitHub 公共仓库仅发布项目源码、测试、运行配置模板和必要页面资源。学校资料 `school-data/`、论文及报告 `docs/`、生成文件 `outputs/`、本机 `.env` 和数据库不随代码上传。下文涉及的资料导入、报告和验证记录需使用自行保留的本地文件；内置示例可直接用于演示。

建议题目：**《基于检索增强生成的校园知识库问答系统设计与实现》**。

## 功能与技术

- 后端：Java 17、Spring Boot 3.5、Spring Security、Spring Data JPA。
- 前端：Vue 3、Vite 6；学生端 `/student`，管理员端 `/admin`。
- 数据：MySQL 8；本地演示和自动测试可用 H2。
- 文档：PDFBox、Apache POI 解析 PDF / DOCX，支持 UTF-8 TXT / Markdown；保存上传原件，SHA-256 文本去重。
- 检索：中文二元分词与 BM25；可选 embedding、余弦相似度与 RRF 混合排名。
- 问答：兼容 OpenAI Chat Completions 接口；未配置时展示检索原文。保存来源快照、历史记录及赞 / 踩评价，支持对话标题搜索、重命名和Markdown导出。
- 管理：上传、修改标题 / 分类 / 来源信息、预览片段、删除、向量重建、运行统计和检索调试。
- 补充路径：范围审核后依次尝试知识库、允许的学校官网页面、明确标记的非官方学术参考，详见 [分级问答设计](docs/answer-routing.md)。

系统采用数据库存储片段和向量，检索时在 Java 中计算，适合小型资料库。本项目没有分布式数据平台、独立向量数据库或自行训练的语言模型。

## 快速运行

要求 JDK 17+、Maven 3.9、Node.js 18.20+；MySQL 模式需 MySQL 8。

1. 在 `backend` 中将 `.env.example` 复制为 `.env`，填写数据库账号和密码。
2. 在两个终端分别运行：

```powershell
# backend 目录
mvn spring-boot:run
```

```powershell
# frontend 目录
npm ci
npm run dev
```

也可双击 `start-backend.cmd` 和 `start-frontend.cmd`。IDEA 以 `backend/pom.xml` 打开 Maven 项目，启动类为 `cn.edu.campus.kb.CampusApplication`，工作目录设置为 `backend`。

- 学生端：http://127.0.0.1:5173/student
- 管理员端：http://127.0.0.1:5173/admin
- 后端：http://127.0.0.1:8080
- 默认管理员：`admin` / `Campus@2026`，通过 `.env` 中 `ADMIN_USERNAME`、`ADMIN_PASSWORD` 设置。

本机已有 `.env` 时继续使用原配置。未配置生成接口时系统可完成本地检索，不要求购买模型接口。临时 H2 模式：在 `backend` 中运行 `mvn spring-boot:run "-Dspring-boot.run.profiles=local"`，数据库保存在 `backend/data/`，与 MySQL 独立。

## 学校资料

归档包可导入 **16份正文和3条Word下载指引**；资料标题、版本、官网原页、PDF / DOCX及哈希见 [资料档案](school-data/sdyu/README.md)。实际资料数以运行数据库为准。

后端启动后，在项目根目录运行：

```powershell
node scripts/import-school.mjs --replace-demo
```

导入前校验哈希，已存在的来源不会重复导入。`--replace-demo` 只删除系统内置虚构示例并保存其备份。原文有年级或版本限定，不能把官网发布日期当作施行日期。下载指引只保留已核对的入口，不宣称已解析验证码附件。

可提问：

- “学士学位平均学分绩点需要达到多少？”
- “2025级学生修满几个学期可以申请转专业？”
- “信息共享空间预约后多久要刷卡登记？”

## 资料维护与学生账号

管理员登录后在知识库上传 PDF、DOCX、TXT、MD，填写标题、分类；可附官网来源、发布单位、发布日期、适用说明和下载链接。单文件最大10 MB、20万字，PDF最多300页，当前最多200份资料。扫描版PDF需先做OCR。

资料列表“编辑”及预览“编辑资料信息”可维护上述字段。新回答采用修改后的信息；历史回答保留原来源快照。未填外部下载地址时使用系统保存的原件，清空外部链接可恢复原件入口。删除资料时同时删除片段与原件，历史快照保留。

学生可注册、登录并跨浏览器查看账号历史。用户名为4—32位字母、数字或下划线，密码8—64字符且UTF-8最多72字节，以BCrypt保存。访客历史按Session隔离，与登录账号历史不自动合并。学生不能访问资料管理、完整文档或管理员统计。

### 历史整理与导出

侧栏可按标题筛选当前账号或访客会话的对话。打开一条对话后，顶部可“重命名对话”（1—80字）和“导出对话”。重命名后刷新仍保留；不改变最后问答时间。导出的Markdown包含消息原文、回答类型、耗时、评价、每轮引用编号及来源快照，便于保存办事依据。

导出由后端校验对话归属，其他用户即使知道ID也无法读取。Markdown以文本围栏保留代码与网页文字；来源URL按快照原样保存，系统内相对下载地址需回到原系统访问。删除资料后仍能导出原引用片段，已删除的原件下载地址会失效。

### 管理员检索调试

在 `/admin` 导航中进入“检索调试”，输入问题查看前5个相关片段、排名、排序分数、实际检索策略及客户端请求耗时，点击“查看完整资料”可定位到片段。该操作只查询知识库，不执行问答范围审核、不调用生成模型、不新增历史；启用向量时会调用embedding接口。

无结果、接口失败与向量降级分别显示；可以取消等待并重新检索。取消的是浏览器等待，服务器已经开始的外部请求可能继续完成。结果分数不是正确率，BM25与RRF分数量纲不同，不应直接比较。

## 模型配置

在 `backend/.env` 中填写兼容接口：

```properties
AI_BASE_URL=https://api.openai.com/v1
AI_API_KEY=你的接口密钥
AI_MODEL=服务商支持的模型名称
AI_EMBEDDING_ENABLED=false
```

Base URL填写接口前缀，不包含 `/chat/completions`。修改后重启后端。启用向量时设置 `AI_EMBEDDING_ENABLED=true` 与 `AI_EMBEDDING_MODEL`，管理员为已有资料重建索引；更换向量模型后重新索引。密钥仅供后端读取。

- 生成调用失败、回答空白或引用编号不合法时回退原文。
- 向量检索失败时回退BM25并披露降级；启用向量的上传失败时整份资料不保存。
- 官网检索默认开启，可由 `WEBSITE_SEARCH_ENABLED=false` 关闭；最多读取4页，不能视为穷尽全校官网。
- 引用编号检查验证格式和范围，不是逐句事实核验。系统统计的“已配置”不代表外部接口已通过健康检查。

检查模型可运行 `node scripts/check-ai.mjs`；外部接口和官网路由实测命令见 [分级问答设计](docs/answer-routing.md)。

## 检查与评测

```powershell
# backend
mvn package

# frontend
npm run build
npm run format:check
npm run test:e2e

# 项目根目录，后端已运行并导入学校资料
node scripts/evaluate-retrieval.mjs
```

后端测试使用独立H2内存数据库。浏览器测试默认Microsoft Edge，连接启动的前后端；学校专项依赖导入资料，外部模型专项依赖有效接口。可用 `E2E_BASE_URL`、`E2E_BROWSER_CHANNEL`、`E2E_ADMIN_USERNAME`、`E2E_ADMIN_PASSWORD` 覆盖配置。

检索评测通过管理员接口取得片段，保存问题标签、排名、Hit@5、MRR@5和请求耗时，详见 [实验说明](docs/evaluation/README.md)。现有12题是试验集，正式实验需扩充和人工核对，不能据此宣称总体准确率。

## 毕设材料与交付

- [系统设计与接口](docs/design.md)
- [本科毕业设计说明、实验计划与演示步骤](docs/graduation-project.md)
- [实际验证记录](docs/verification.md)
- [开题报告及学校模板依据](docs/proposal/README.md)
- [写作规范原件及逐项检查记录](docs/standards/README.md)（5份标准PDF；学校同名撰写规范全文待补充）

项目根目录运行 `python scripts/project-maintenance.py clean` 清理可再生成产物；运行 `python scripts/project-maintenance.py export` 生成 `deliverables/campus-knowledge-base-source.zip`。导出包按白名单包含源码、学校资料、报告和配置模板，排除本机 `.env`、数据库、依赖、IDE与工具配置。

构建后端产物为 `backend/target/campus-knowledge-base-1.0.0.jar`，前端为 `frontend/dist/`。部署静态页面时将 `/api` 反向代理到Java服务，`/admin`、`/student` 回退到 `index.html`，保持同源Cookie与CSRF访问。

连接失败时检查后端和端口；数据库错误时检查工作目录与 `.env`；会话过期时重新登录并刷新页面。`PORT` 可更换后端端口，前端通过 `API_PROXY_TARGET` 指定代理目标。
