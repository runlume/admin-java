package app.runlume.admin;

import app.runlume.admin.support.PostgresTestSupport;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 每张表与每一列都必须有中文 Catalog 注释。
 *
 * <p>表与列的业务含义只写在数据库 Catalog 里，读库和读迁移的人才能就地理解字段；注释缺失不会让
 * 任何功能失败，因此必须由门禁保证，而不是依赖评审记得检查。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 21:05
 */
class DatabaseCatalogCommentTests extends PostgresTestSupport {

    private static final Pattern HAN_CHARACTER = Pattern.compile("\\p{IsHan}");

    @Autowired
    private DSLContext dsl;

    @Test
    void everyTableCarriesChineseCatalogComment() {
        assertThat(undocumented("""
                select c.relname, coalesce(obj_description(c.oid), '')
                from pg_class c
                join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'public'
                  and c.relkind = 'r'
                  -- Flyway 自己的迁移历史表不由本项目拥有，也不需要本项目维护注释。
                  and c.relname <> 'flyway_schema_history'
                order by c.relname
                """)).isEmpty();
    }

    @Test
    void everyColumnCarriesChineseCatalogComment() {
        assertThat(undocumented("""
                select c.relname || '.' || a.attname,
                       coalesce(col_description(c.oid, a.attnum), '')
                from pg_class c
                join pg_namespace n on n.oid = c.relnamespace
                join pg_attribute a on a.attrelid = c.oid
                    and a.attnum > 0
                    and not a.attisdropped
                where n.nspname = 'public'
                  and c.relkind = 'r'
                  and c.relname <> 'flyway_schema_history'
                order by 1
                """)).isEmpty();
    }

    private List<String> undocumented(String sql) {
        return dsl.fetch(sql).stream()
                .filter(record -> !HAN_CHARACTER.matcher(comment(record)).find())
                .map(record -> record.get(0, String.class))
                .toList();
    }

    private static String comment(Record record) {
        String value = record.get(1, String.class);
        return value == null ? "" : value;
    }
}
