package com.lgk.lgkaicodeservice.datasource;

import com.lgk.lgkaicodeservice.model.dto.post.PostQueryRequest;
import com.lgk.lgkaicodeservice.model.vo.PostVO;
import com.lgk.lgkaicodeservice.service.PostService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

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

        //TODO 后面改成从 es 中查询，先查静态数据，后面补上动态的数据（点赞、收藏、浏览等等）

        return postService.listPostVOByPage(postQueryRequest);
    }
}
