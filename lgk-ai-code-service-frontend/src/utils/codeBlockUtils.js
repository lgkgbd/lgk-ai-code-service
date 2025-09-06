// 可折叠代码块工具函数
/**
 * 初始化代码块切换功能
 */
export function initCodeBlockToggle() {
  if (typeof window !== 'undefined') {
    window.toggleCodeBlock = (codeId) => {
      const codeContent = document.getElementById(codeId)
      const toggleIcon = document.getElementById(`icon-${codeId}`)
      const codeBlock = codeContent?.closest('.collapsible-code-block')
      
      if (codeContent && toggleIcon && codeBlock) {
        const isExpanded = codeContent.classList.contains('expanded')
        
        if (isExpanded) {
          // 折叠
          codeContent.classList.remove('expanded')
          codeBlock.classList.remove('expanded')
          toggleIcon.innerHTML = '▶'
        } else {
          // 展开
          codeContent.classList.add('expanded')
          codeBlock.classList.add('expanded')
          toggleIcon.innerHTML = '▼'
        }
      }
    }
  }
}

/**
 * 创建可折叠代码块HTML
 * @param {string} str - 原始代码字符串
 * @param {string} lang - 语言类型
 * @param {string} highlightedCode - 高亮后的代码
 * @returns {string} HTML字符串
 */
export function createCollapsibleCodeBlock(str, lang, highlightedCode) {
  const codeId = `code-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
  
  return `
    <div class="collapsible-code-block expanded">
      <div class="code-header" onclick="toggleCodeBlock('${codeId}')">
        <div class="code-header-left">
          <span class="code-language">${lang || 'text'}</span>
        </div>
        <span class="toggle-icon" id="icon-${codeId}">▼</span>
      </div>
      <div class="code-content expanded" id="${codeId}">
        <pre class="code-block"><code class="language-${lang || 'text'}">${highlightedCode}</code></pre>
      </div>
    </div>
  `
}