// 可折叠代码块工具函数
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
          toggleIcon.textContent = '▶️'
        } else {
          // 展开
          codeContent.classList.add('expanded')
          codeBlock.classList.add('expanded')
          toggleIcon.textContent = '🔽'
        }
      }
    }
  }
}

// 创建可折叠代码块HTML
export function createCollapsibleCodeBlock(str, lang, highlightedCode) {
  const codeId = `code-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
  
  return `
    <div class="collapsible-code-block">
      <div class="code-header" onclick="toggleCodeBlock('${codeId}')">
        <span class="code-language">${lang || 'text'}</span>
        <span class="toggle-icon" id="icon-${codeId}">▶️</span>
      </div>
      <div class="code-content" id="${codeId}">
        <pre class="code-block"><code class="language-${lang || 'text'}">${highlightedCode}</code></pre>
      </div>
    </div>
  `
}