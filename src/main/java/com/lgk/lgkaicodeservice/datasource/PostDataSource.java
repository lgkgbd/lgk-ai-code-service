package com.lgk.lgkaicodeservice.datasource;

import com.lgk.lgkaicodeservice.model.dto.post.PostQueryRequest;
import com.lgk.lgkaicodeservice.model.entity.Post;
import com.lgk.lgkaicodeservice.model.vo.PostVO;
import com.lgk.lgkaicodeservice.service.PostService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class PostDataSource implements DataSource<PostVO>{

    @Resource
    private PostService postService;

    @Override
    public Page<PostVO> doSearch(String searchText, long pageNum, long pageSize) {

        PostQueryRequest postQueryRequest = new PostQueryRequest();
        postQueryRequest.setSearchText(searchText);
        postQueryRequest.setPageNum(pageNum);
        postQueryRequest.setPageSize(pageSize);

        ServletRequestAttributes servletRequestAttributes =  (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = servletRequestAttributes.getRequest();
        Page<Post> postPage = postService.searchFromEs(postQueryRequest);

        return postService.getPostVOPage(postPage,request);
    }
}
