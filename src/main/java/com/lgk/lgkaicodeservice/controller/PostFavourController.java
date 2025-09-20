package com.lgk.lgkaicodeservice.controller;

import com.mybatisflex.core.paginate.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.lgk.lgkaicodeservice.model.entity.PostFavour;
import com.lgk.lgkaicodeservice.service.PostFavourService;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 帖子收藏表 控制层。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@RestController
@RequestMapping("/postFavour")
public class PostFavourController {

    @Autowired
    private PostFavourService postFavourService;

    /**
     * 保存帖子收藏表。
     *
     * @param postFavour 帖子收藏表
     * @return {@code true} 保存成功，{@code false} 保存失败
     */
    @PostMapping("save")
    public boolean save(@RequestBody PostFavour postFavour) {
        return postFavourService.save(postFavour);
    }

    /**
     * 根据主键删除帖子收藏表。
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("remove/{id}")
    public boolean remove(@PathVariable Long id) {
        return postFavourService.removeById(id);
    }

    /**
     * 根据主键更新帖子收藏表。
     *
     * @param postFavour 帖子收藏表
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("update")
    public boolean update(@RequestBody PostFavour postFavour) {
        return postFavourService.updateById(postFavour);
    }

    /**
     * 查询所有帖子收藏表。
     *
     * @return 所有数据
     */
    @GetMapping("list")
    public List<PostFavour> list() {
        return postFavourService.list();
    }

    /**
     * 根据主键获取帖子收藏表。
     *
     * @param id 帖子收藏表主键
     * @return 帖子收藏表详情
     */
    @GetMapping("getInfo/{id}")
    public PostFavour getInfo(@PathVariable Long id) {
        return postFavourService.getById(id);
    }

    /**
     * 分页查询帖子收藏表。
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("page")
    public Page<PostFavour> page(Page<PostFavour> page) {
        return postFavourService.page(page);
    }

}
