package config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Paths;

/**
 * Maps the external `frontend/` directory (outside the classpath) so Spring can serve the built static assets.
 * Also forwards the root path `/` to `index.html` (SPA entry point).
 */
@Configuration
public class WebStaticConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Absolute path to the frontend folder on disk (Windows-safe). Must end with separator for Spring to treat as a directory.
        String frontPath = Paths.get("frontend").toFile().getAbsolutePath() + File.separator;

        // Expose only needed static directories + html entry points. Avoid pattern "/" which conflicts with controllers.
        registry
            .addResourceHandler(
                "/index.html",
                "/*.html",
                "/css/**",
                "/js/**",
                "/img/**"
            )
            // Ensure absolute file URL has leading slash after file: for Windows (file:/C:/...)
            .addResourceLocations("file:/" + frontPath.replace("\\", "/"))
            .setCachePeriod(0); // disable caching in dev for quick iterations
    }

    @Override
    public void addViewControllers(@NonNull ViewControllerRegistry registry) {
        // Forward root requests to SPA entry (so navigating to http://localhost:8080/ loads index.html)
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
