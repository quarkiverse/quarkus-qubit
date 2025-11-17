package io.quarkus.qusaq.it;

import io.quarkus.qusaq.runtime.QusaqEntity;
import jakarta.persistence.Entity;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Another test entity for comprehensive query testing with Qusaq.
 *
 * <p>This entity extends {@link QusaqEntity} to gain lambda-based
 * query methods. The abstract methods are implemented at build time.
 */
@Entity
public class Product extends QusaqEntity {

    public String name;
    public String category;
    public BigDecimal price;
    public int stockQuantity;
    public boolean available;
    public String description;
    public Double rating;

    public Product() {
    }

    public Product(String name, String category, BigDecimal price,
                   int stockQuantity, boolean available, String description, Double rating) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.available = available;
        this.description = description;
        this.rating = rating;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getDescription() {
        return description;
    }

    public Double getRating() {
        return rating;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product product)) return false;
        return stockQuantity == product.stockQuantity &&
               available == product.available &&
               Objects.equals(id, product.id) &&
               Objects.equals(name, product.name) &&
               Objects.equals(category, product.category) &&
               Objects.equals(price, product.price) &&
               Objects.equals(description, product.description) &&
               Objects.equals(rating, product.rating);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, category, price, stockQuantity, available, description, rating);
    }

    @Override
    public String toString() {
        return "Product{" +
               "id=" + id +
               ", name='" + name + '\'' +
               ", category='" + category + '\'' +
               ", price=" + price +
               ", stockQuantity=" + stockQuantity +
               ", available=" + available +
               ", description='" + description + '\'' +
               ", rating=" + rating +
               '}';
    }
}
