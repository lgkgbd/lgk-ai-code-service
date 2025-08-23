/**
 * 调试工具 - 帮助验证iframe同源问题
 */
export class DebugHelper {
  /**
   * 检查iframe是否同源
   */
  static checkIframeSameOrigin(iframe: HTMLIFrameElement): boolean {
    try {
      // 尝试访问iframe的contentDocument
      const doc = iframe.contentDocument
      return doc !== null
    } catch (error) {
      console.error('DebugHelper: iframe is not same-origin:', error)
      return false
    }
  }

  /**
   * 检查iframe是否可以注入脚本
   */
  static testScriptInjection(iframe: HTMLIFrameElement): boolean {
    try {
      if (!iframe.contentDocument) {
        console.error('DebugHelper: cannot access iframe contentDocument')
        return false
      }

      // 尝试创建一个简单的script元素
      const script = iframe.contentDocument.createElement('script')
      script.textContent = 'console.log("DebugHelper: script injection test successful")'
      iframe.contentDocument.head.appendChild(script)

      console.log('DebugHelper: script injection test passed')
      return true
    } catch (error) {
      console.error('DebugHelper: script injection test failed:', error)
      return false
    }
  }

  /**
   * 检查iframe的URL信息
   */
  static logIframeInfo(iframe: HTMLIFrameElement): void {
    console.log('DebugHelper: iframe info:', {
      src: iframe.src,
      currentSrc: iframe.currentSrc,
      contentWindow: !!iframe.contentWindow,
      contentDocument: !!iframe.contentDocument,
      sameOrigin: this.checkIframeSameOrigin(iframe)
    })
  }

  /**
   * 完整的iframe调试
   */
  static debugIframe(iframe: HTMLIFrameElement): void {
    console.log('=== DebugHelper: iframe debug start ===')
    this.logIframeInfo(iframe)

    if (this.checkIframeSameOrigin(iframe)) {
      console.log('DebugHelper: iframe is same-origin, testing script injection...')
      this.testScriptInjection(iframe)
    } else {
      console.error('DebugHelper: iframe is NOT same-origin - this will prevent visual editing')
      console.error('DebugHelper: make sure vite proxy is configured correctly')
    }

    console.log('=== DebugHelper: iframe debug end ===')
  }
}
