package diamond.cms.server.services;

import static diamond.cms.server.model.jooq.Tables.C_ARTICLE;
import static diamond.cms.server.model.jooq.Tables.C_CATALOG;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.Condition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import diamond.cms.server.core.PageResult;
import diamond.cms.server.dao.Fields;
import diamond.cms.server.model.Article;
import diamond.cms.server.model.ArticleDetail;
import diamond.cms.server.model.ArticleTag;
import diamond.cms.server.model.Tag;
import diamond.cms.server.model.jooq.tables.CArticle;

@Service
public class ArticleService extends GenericService<Article>{

    @Autowired
    TagService tagService;

    @Autowired
    ArticleTagService articleTagService;

    @Override
    public Article get(String id) {
        List<String> ids = articleTagService.findTagIds(id).stream().map(ArticleTag::getTagId).collect(Collectors.toList());
        Article article  = super.get(id);
        article.setTagIds(ids.toArray(new String[ids.size()]));
        return article;
    }

    @Override
    public Article save(Article article) {
        String [] tagIds = article.getTagIds();
        article = super.save(article);
        saveArticleTags(article.getId(), tagIds);
        return article;
    }

    private void saveArticleTags(String articleId, String[] tagIds) {
        if (tagIds != null) {
            List<Tag> tags = tagService.saveTagIfNotExists(tagIds);
            List<ArticleTag> tagList = new ArrayList<>();
            tags.stream().map(Tag::getId).forEach(tagid -> {
                ArticleTag artTag = new ArticleTag();
                artTag.setArticleId(articleId);
                artTag.setTagId(tagid);
                tagList.add(artTag);
            });
            articleTagService.deleteByArticleId(articleId);
            articleTagService.insert(tagList);
        }
    }

    @Override
    public Article update(Article entity) {
        saveArticleTags(entity.getId(), entity.getTagIds());
        entity.setUpdateTime(currentTime());
        entity.setCreateTime(null);
        if (entity.getBanner() == null) {
           entity.setBanner("");
        }
        return super.update(entity);
    }

    @Override
    public PageResult<Article> page(PageResult<Article> page) {
        return searchPageByCondition(page, Stream.of(C_ARTICLE.STATUS.in(Arrays.asList(new Integer[]{Article.STATUS_PUBLISH, Article.STATUS_UNPUBLISH}))));
    }

    @Override
    public int delete(String id) {
        return updateStatus(id, Article.STATUS_DELETE);
    }

    public Integer updateStatus(String id, int status) {
        return dao.execute(e -> {
           return e.update(C_ARTICLE).set(C_ARTICLE.STATUS, status)
           .where(C_ARTICLE.ID.eq(id))
           .execute();
        });
    }


    public PageResult<Article> page(PageResult<Article> page, Integer status, Optional<String> catalogId) {
        Condition cond = C_ARTICLE.STATUS.eq(status);
        if (catalogId.isPresent()) {
            String cid = catalogId.get();
            if ("-1".equals(cid)) {
                cond = cond.and(C_ARTICLE.CATALOG_ID.isNull().or(C_ARTICLE.CATALOG_ID.eq("")));
            } else if (!"0".equals(cid)) {
                cond = cond.and(C_ARTICLE.CATALOG_ID.eq(cid));
            }
        }
        return searchPageByCondition(page, Stream.of(cond));
    }

    private PageResult<Article> searchPageByCondition(PageResult<Article> page, Stream<Condition> cond) {
        page = dao.fetch(page, e -> {
            return e.select(Fields.all(C_ARTICLE.fields(),C_CATALOG.NAME.as("catalogName")))
            .from(C_ARTICLE)
            .leftJoin(C_CATALOG).on(C_ARTICLE.CATALOG_ID.eq(C_CATALOG.ID))
            .where(cond.collect(Collectors.toList()))
            .orderBy(C_ARTICLE.CREATE_TIME.desc());
        }, Article.class);
        List<Article> articles = page.getData();
        if (!articles.isEmpty()) {
            Map<String,Article> articlesMap = articles.stream().collect(Collectors.toMap(Article::getId, a -> {
                a.setTags(new ArrayList<>());
                return a;
            }));
            List<ArticleTag> articleTags = articleTagService.findTags(articlesMap.keySet());
            articleTags.forEach(articleTag -> {
                articlesMap.get(articleTag.getArticleId()).getTags().add(articleTag.getTag());
            });
        }
        return page;
    }

    public Article saveDraft(Article article) {
        if (article.getId() == null) {
            article = save(article);
        } else {
            article = update(article);
        }
        return article;
    }

    public ArticleDetail getDetail(String id) {
        CArticle article = C_ARTICLE.as("a");
        CArticle before = C_ARTICLE.as("b");
        CArticle next = C_ARTICLE.as("n");
        CArticle inner = C_ARTICLE.as("i");
        ArticleDetail a = dao.execute(e -> {
            return e.select(Fields.all(article.fields(),
                before.ID.as("beforeId"),
                before.TITLE.as("beforeTitle"),
                next.ID.as("nextId"),
                next.TITLE.as("nextTitle")
                )).from(article)
                .leftJoin(before).on(before.ID.eq(e.select(inner.ID).from(inner).where(article.CREATE_TIME.ge(inner.CREATE_TIME)).and(inner.ID.ne(article.ID).and(inner.STATUS.eq(Article.STATUS_PUBLISH))).orderBy(inner.CREATE_TIME.desc()).limit(0, 1)))
                .leftJoin(next).on((next.ID.eq(e.select(inner.ID).from(inner).where(article.CREATE_TIME.le(inner.CREATE_TIME)).and(inner.ID.ne(article.ID).and(inner.STATUS.eq(Article.STATUS_PUBLISH))).orderBy(inner.CREATE_TIME).limit(0, 1))))
                .where(article.ID.eq(id))
                .fetchOne(r -> {
                    return dao.mapperEntityEx(r, ArticleDetail.class);
                });
        });
        List<Tag> tags = articleTagService.findTags(a.getId());
        a.setTags(tags);
        return a;
    }

    public Integer updateCreateTime(String id, Long time) {
        return dao.execute(e -> {
            return e.update(C_ARTICLE)
            .set(C_ARTICLE.CREATE_TIME, new Timestamp(time))
            .where(C_ARTICLE.ID.eq(id))
            .execute();
        });
    }

    public List<Article> findIdTitle() {
        return dao.execute(e -> {
            return e.select(Fields.all(C_ARTICLE.ID, C_ARTICLE.TITLE))
            .from(C_ARTICLE)
            .fetchInto(Article.class);
        });
    }

    public List<Article> findArticleSite() {
        CArticle t = C_ARTICLE;
        return dao.execute(e -> {
            return e.select(Fields.all(t.ID, t.TITLE, t.UPDATE_TIME))
            .from(t)
            .where(t.STATUS.eq(Article.STATUS_PUBLISH))
            .fetchInto(Article.class);
        });
    }

}
