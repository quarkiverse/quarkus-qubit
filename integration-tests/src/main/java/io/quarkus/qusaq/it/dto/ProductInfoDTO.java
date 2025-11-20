package io.quarkus.qusaq.it.dto;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * DTO for testing constructor-based projections with Product entity.
 * <p>
 * Used to test: {@code Product.select(p -> new ProductInfoDTO(p.name, p.price, p.category)).toList()}
 */
public class ProductInfoDTO {

    private final String name;
    private final BigDecimal price;
    private final String category;

    public ProductInfoDTO(String name, BigDecimal price, String category) {
        this.name = name;
        this.price = price;
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductInfoDTO that)) return false;
        return Objects.equals(name, that.name) &&
               Objects.equals(price, that.price) &&
               Objects.equals(category, that.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, price, category);
    }

    @Override
    public String toString() {
        return "ProductInfoDTO{name='" + name + "', price=" + price + ", category='" + category + "'}";
    }
}
