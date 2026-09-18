package app.runlume.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Runlume 标准后台后端启动类。
 *
 * <p>顶层包结构遵循业务系统 access 模块标准：{@code access} 是平台集成 owning module，
 * 业务域与它是兄弟包，只能通过 Named Interface 访问身份与会话事实。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:10
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AdminJavaApplication {

    /**
     * 启动应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AdminJavaApplication.class, args);
    }
}
