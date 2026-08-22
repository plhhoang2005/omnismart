package vn.omnismart.catalog;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductCatalogConfiguration {

    @Bean
    @ConditionalOnMissingBean(ProductPublicationGuard.class)
    ProductPublicationGuard noActivePublicationJobs() {
        return (storeId, productId) -> false;
    }
}
