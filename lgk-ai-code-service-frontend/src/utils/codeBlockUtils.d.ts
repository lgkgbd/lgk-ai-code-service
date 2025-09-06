/**
 * 初始化代码块切换功能
 */
export function initCodeBlockToggle(): void;

/**
 * 创建可折叠代码块HTML
 * @param str - 原始代码字符串
 * @param lang - 语言类型
 * @param highlightedCode - 高亮后的代码
 * @returns HTML字符串
 */
export function createCollapsibleCodeBlock(str: string, lang: string, highlightedCode: string): string;