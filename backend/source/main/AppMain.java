import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
    scanBasePackages = {
        "controller",
        "service",
        "repository",
        "mapper",
        "config",
        "entity",
        "dto",
        "enums",
        "exception"
    }
)
@EnableJpaRepositories(basePackages = {"repository"})
@EntityScan(basePackages = {"entity"})
public class AppMain {
    public static void main(String[] args) {
        SpringApplication.run(AppMain.class, args);
    }
}
