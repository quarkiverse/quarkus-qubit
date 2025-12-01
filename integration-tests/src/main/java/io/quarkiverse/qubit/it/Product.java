package io.quarkiverse.qubit.it;

import io.quarkiverse.qubit.runtime.QubitEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Another test entity for comprehensive query testing with Qubit.
 *
 * <p>This entity extends {@link QubitEntity} to gain lambda-based
 * query methods. The abstract methods are implemented at build time.
 */
@Entity
public class Product extends QubitEntity {

    public String name;
    public String category;
    public BigDecimal price;
    public int stockQuantity;
    public boolean available;
    public String description;
    public Double rating;

    @ManyToMany
    @JoinTable(
            name = "product_tag",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    public Set<Tag> tags = new HashSet<>();

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

    public Set<Tag> getTags() {
        return tags;
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
