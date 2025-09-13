declare namespace API {
  type AppAddRequest = {
    initPrompt?: string
  }

  type AppAdminUpdateRequest = {
    id?: number
    appName?: string
    cover?: string
    priority?: number
  }

  type AppDeployRequest = {
    appId?: number
  }

  type AppQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
    deployKey?: string
    priority?: number
    userId?: number
  }

  type AppUpdateRequest = {
    id?: number
    appName?: string
  }

  type AppVO = {
    id?: number
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
    deployKey?: string
    deployedTime?: string
    priority?: number
    userId?: number
    createTime?: string
    updateTime?: string
    user?: UserVO
  }

  type BaseResponseAppVO = {
    code?: number
    data?: AppVO
    message?: string
  }

  type BaseResponseBoolean = {
    code?: number
    data?: boolean
    message?: string
  }

  type BaseResponseListInteger = {
    code?: number
    data?: number[]
    message?: string
  }

  type BaseResponseLoginUserVO = {
    code?: number
    data?: LoginUserVO
    message?: string
  }

  type BaseResponseLong = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponsePageAppVO = {
    code?: number
    data?: PageAppVO
    message?: string
  }

  type BaseResponsePageChatHistory = {
    code?: number
    data?: PageChatHistory
    message?: string
  }

  type BaseResponsePagePostVO = {
    code?: number
    data?: PagePostVO
    message?: string
  }

  type BaseResponsePageUserVO = {
    code?: number
    data?: PageUserVO
    message?: string
  }

  type BaseResponsePostVO = {
    code?: number
    data?: PostVO
    message?: string
  }

  type BaseResponseString = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponseUser = {
    code?: number
    data?: User
    message?: string
  }

  type BaseResponseUserVO = {
    code?: number
    data?: UserVO
    message?: string
  }

  type ChatHistory = {
    id?: number
    message?: string
    messageType?: string
    appId?: number
    userId?: number
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type ChatHistoryQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    message?: string
    messageType?: string
    appId?: number
    userId?: number
    lastCreateTime?: string
  }

  type chatToGenCodeParams = {
    appId: number
    message: string
  }

  type DeleteRequest = {
    id?: number
  }

  type DiagramTask = {
    mermaidCode?: string
    description?: string
  }

  type downloadAppCodeParams = {
    appId: number
  }

  type executeWorkflowParams = {
    prompt: string
  }

  type executeWorkflowWithFluxParams = {
    prompt: string
  }

  type executeWorkflowWithSseParams = {
    prompt: string
  }

  type getAppVOByIdByAdminParams = {
    id: number
  }

  type getAppVOByIdParams = {
    id: number
  }

  type getInfoParams = {
    id: number
  }

  type getPostVOByIdParams = {
    id: number
  }

  type getUserByIdParams = {
    id: number
  }

  type getUserSignInRecordParams = {
    year: number
  }

  type getUserVOByIdParams = {
    id: number
  }

  type IllustrationTask = {
    query?: string
  }

  type ImageCollectionPlan = {
    contentImageTasks?: ImageSearchTask[]
    illustrationTasks?: IllustrationTask[]
    diagramTasks?: DiagramTask[]
    logoTasks?: LogoTask[]
  }

  type ImageResource = {
    category?: 'CONTENT' | 'LOGO' | 'ILLUSTRATION' | 'ARCHITECTURE'
    description?: string
    url?: string
  }

  type ImageSearchTask = {
    query?: string
  }

  type listAppChatHistoryParams = {
    appId: number
    pageSize?: number
    lastCreateTime?: string
  }

  type LoginUserVO = {
    id?: number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
    updateTime?: string
  }

  type LogoTask = {
    description?: string
  }

  type PageAppVO = {
    records?: AppVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageChatHistory = {
    records?: ChatHistory[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type pageParams = {
    page: PageChatHistory
  }

  type PagePostVO = {
    records?: PostVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageUserVO = {
    records?: UserVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PostAddRequest = {
    title?: string
    content?: string
    tags?: string[]
    coverImage?: string
  }

  type PostQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    searchText?: string
    tags?: string[]
    userId?: number
    priority?: number
  }

  type PostUpdateRequest = {
    id?: number
    title?: string
    content?: string
    tags?: string[]
    coverImage?: string
  }

  type PostVO = {
    id?: number
    title?: string
    content?: string
    tags?: string[]
    coverImage?: string
    thumbNum?: number
    favourNum?: number
    viewNum?: number
    userId?: number
    user?: UserVO
    createTime?: string
    updateTime?: string
    priority?: number
  }

  type QualityResult = {
    isValid?: boolean
    errors?: string[]
    suggestions?: string[]
  }

  type removeParams = {
    id: number
  }

  type ServerSentEventString = true

  type serveStaticResourceParams = {
    deployKey: string
  }

  type SseEmitter = {
    timeout?: number
  }

  type User = {
    id?: number
    userAccount?: string
    userPassword?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    editTime?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type UserAddRequest = {
    userName?: string
    userAccount?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type UserQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    userName?: string
    userAccount?: string
    userProfile?: string
    userRole?: string
  }

  type UserRegisterRequest = {
    userAccount?: string
    userPassword?: string
    checkPassword?: string
  }

  type UserUpdateRequest = {
    id?: number
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserVO = {
    id?: number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
  }

  type WorkflowContext = {
    currentStep?: string
    originalPrompt?: string
    imageListStr?: string
    imageList?: ImageResource[]
    enhancedPrompt?: string
    qualityResult?: QualityResult
    imageCollectionPlan?: ImageCollectionPlan
    contentImages?: ImageResource[]
    illustrations?: ImageResource[]
    diagrams?: ImageResource[]
    logos?: ImageResource[]
    generationType?: 'HTML' | 'MULTI_FILE' | 'VUE_PROJECT'
    generatedCodeDir?: string
    buildResultDir?: string
    errorMessage?: string
  }
}

declare module 'vditor' {
  class Vditor {
    constructor(id: string, options?: any);
    getValue(): string;
    // Add other methods you use here
  }
  export default Vditor;
}
