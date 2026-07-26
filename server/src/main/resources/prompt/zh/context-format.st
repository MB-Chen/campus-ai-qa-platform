# 本文件用 section 复用：单文件多片段，供 PromptTemplateLoader.renderSection 调用。
# 不要整体渲染本文件，只取需要的 section。

--- section: documents ---
<documents>
{documents}
</documents>

--- section: tool-data ---
<tool-data>
{tool_data}
</tool-data>

--- section: summary-wrapper ---
<conversation-summary>
{summary}
</conversation-summary>

--- section: single-question ---
<question>{question}</question>

--- section: multi-questions ---
<questions>
{questions}
</questions>