package diamond.cms.server.services;

import static diamond.cms.server.model.jooq.Tables.C_ARTICLE;
import static diamond.cms.server.model.jooq.Tables.C_CATALOG;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Optional;

import javax.annotation.Resource;

import org.junit.Assert;
import org.junit.Test;

import diamond.cms.server.BasicTestCase;
import diamond.cms.server.core.PageResult;
import diamond.cms.server.dao.CommonDao;
import diamond.cms.server.dao.Fields;
import diamond.cms.server.model.Article;
import diamond.cms.server.model.ArticleDetail;

public class ArticleServiceTest extends BasicTestCase{

    @Resource
    ArticleService articleService;

    @Resource
    CommonDao<Article> articleDao;

    @Test
    public void buildTagNames () {
        articleService.buildTagNames();
    }

    @Test
    public void search() {
        String kw1 = "的";
        articleService.page(new PageResult<>(), Article.STATUS_PUBLISH, Optional.empty(), kw1);
    }

    @Test
    public void findTest(){
        articleService.page(new PageResult<Article>());
    }


    @Test
    public void findPerformanceTestReflect(){
        long begin = System.currentTimeMillis();
        articleDao.fetch(new PageResult<Article>(), e -> {
            return e.select(Fields.all(C_ARTICLE.fields(),C_CATALOG.NAME.as("catalogName")))
            .from(C_ARTICLE)
            .leftJoin(C_CATALOG).on(C_ARTICLE.CATALOG_ID.eq(C_CATALOG.ID));
        }, Article.class);
        long time = System.currentTimeMillis() - begin;
        log.info("findPerformanceTestReflect for :" + time +" ms");
    }

    @Test
    public void findPerformanceTestInterface() {
        long begin = System.currentTimeMillis();
        articleDao.fetch(new PageResult<Article>(), e -> {
            return e.select(Fields.all(C_ARTICLE.fields(),C_CATALOG.NAME))
            .from(C_ARTICLE)
            .leftJoin(C_CATALOG).on(C_ARTICLE.CATALOG_ID.eq(C_CATALOG.ID));
        }, r -> {
            Article a = r.into(Article.class);
            a.setCatalogName(r.getValue(C_CATALOG.NAME));
            return a;
        });
        long time = System.currentTimeMillis() - begin;
        log.info("findPerformanceTestInterface for :" + time +" ms");
    }

    @Test
    public void testDelete() {
        Article a = new Article();
        a.setTitle("hello");
        articleService.save(a);
        boolean isUnpublish = articleService.get(a.getId()).getStatus().equals(Article.STATUS_UNPUBLISH);
        Assert.assertTrue("default must be unpublish", isUnpublish);
        articleService.delete(a.getId());
        boolean isDelete = articleService.get(a.getId()).getStatus().equals(Article.STATUS_DELETE);
        Assert.assertTrue("delete status error", isDelete);
    }

    @Test
    public void statusTest() {
        articleService.page(new PageResult<>(), Optional.of(Article.STATUS_PUBLISH)).getData().forEach(article -> {
           Assert.assertTrue("article is delete", !article.getStatus().equals(Article.STATUS_DELETE));
        });
    }

    @Test
    public void saveTest() {
        Article art = new Article();
        art.setTitle("test");
        art.setSummary("");
        art.setContent("test1");
        String [] tagIds = new String [10];
        for(int i = 0; i < tagIds.length; i ++){
            tagIds[i] = "testtag" + i;
        }
        art.setTagIds(tagIds);
        art = articleService.save(art);
        String tagNames = tagsToNames(tagIds);
        log.info(art.getTagNames());
        log.info(tagNames);
        Assert.assertEquals("save tagNames must build: " + tagNames, art.getTagNames().length(), tagNames.length());
        tagIds = Arrays.copyOf(tagIds, 4);
        art.setTagIds(tagIds);
        art = articleService.update(art);
        tagNames = tagsToNames(tagIds);
        log.info(art.getTagNames());
        log.info(tagNames);
        Assert.assertEquals("update tagNames must build: " + tagNames, art.getTagNames().length(), tagNames.length());
    }

    private String tagsToNames(String[] tagIds) {
        return String.join(",", tagIds);
    }


    @Test
    public void getDetail() {

        Article before = new Article();
        before.setStatus(Article.STATUS_PUBLISH);
        before.setTitle("before art1");
        before.setCreateTime(new Timestamp(System.currentTimeMillis() - 10000));

        Article now = new Article();
        now.setStatus(Article.STATUS_PUBLISH);
        now.setTitle("art1");
        now.setCreateTime(new Timestamp(System.currentTimeMillis()));

        Article next = new Article();
        next.setStatus(Article.STATUS_PUBLISH);
        next.setTitle("next art1");
        next.setCreateTime(new Timestamp(System.currentTimeMillis() + 10000));

        Article next2 = new Article();
        next2.setTitle("next 222 art1");
        next2.setStatus(Article.STATUS_PUBLISH);
        next2.setCreateTime(new Timestamp(System.currentTimeMillis() + 50000));


        articleService.save(before);
        articleService.save(now);
        articleService.save(next);
        articleService.save(next2);

        ArticleDetail detail  = articleService.getDetail(now.getId());
        Assert.assertTrue("not find next" + detail.getNextId(), detail.getNextId().equals(next.getId()));
        Assert.assertTrue("not find before" + detail.getNextId(), detail.getBeforeId().equals(before.getId()));
        detail = articleService.getDetail(next2.getId());
        Assert.assertNotNull("must find detail", detail.getId());
        Assert.assertEquals("not before eq", detail.getBeforeId(), next.getId());
        Assert.assertNull("next must null now" + detail.getNextId(), detail.getNextId());
    }

    @Test
    public void page() {
        articleService.page(new PageResult<>());
    }
}
