package config.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve static resources but exclude /api paths
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(0) // Disable cache for development
                .resourceChain(true)
                .addResolver(new org.springframework.web.servlet.resource.PathResourceResolver() {
                    @Override
                    protected org.springframework.core.io.Resource getResource(String resourcePath,
                            org.springframework.core.io.Resource location) throws java.io.IOException {
                        // Don't serve API paths as static resources
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        return super.getResource(resourcePath, location);
                    }
                });
    }
}

