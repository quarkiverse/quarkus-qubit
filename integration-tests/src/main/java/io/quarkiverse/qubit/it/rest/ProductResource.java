package io.quarkiverse.qubit.it.rest;

import io.quarkiverse.qubit.it.Product;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST resource for Product queries to test Qubit extension in native mode.
 * Each endpoint exercises specific Qubit query operations.
 */
@Path("/api/products")
@Produces(MediaType.APPLICATION_JSON)
public class ProductResource {

    // =============================================================================================
    // BASIC QUERIES
    // =============================================================================================

    @GET
    public List<Product> getAll() {
        return Product.listAll();
    }

    @GET
    @Path("/count")
    public long count() {
        return Product.count();
    }

    @GET
    @Path("/available")
    public List<Product> getAvailable() {
        return Product.where((Product p) -> p.available).toList();
    }

    @GET
    @Path("/unavailable")
    public List<Product> getUnavailable() {
        return Product.where((Product p) -> !p.available).toList();
    }

    @GET
    @Path("/in-stock")
    public List<Product> getInStock() {
        return Product.where((Product p) -> p.stockQuantity > 0).toList();
    }

    // =============================================================================================
    // BIGDECIMAL OPERATIONS
    // =============================================================================================

    @GET
    @Path("/price-greater-than/{price}")
    public List<Product> getPriceGreaterThan(@PathParam("price") String price) {
        BigDecimal threshold = new BigDecimal(price);
        return Product.where((Product p) -> p.price.compareTo(threshold) > 0).toList();
    }

    @GET
    @Path("/price-less-than/{price}")
    public List<Product> getPriceLessThan(@PathParam("price") String price) {
        BigDecimal threshold = new BigDecimal(price);
        return Product.where((Product p) -> p.price.compareTo(threshold) < 0).toList();
    }

    @GET
    @Path("/price-between/{min}/{max}")
    public List<Product> getPriceBetween(@PathParam("min") String min, @PathParam("max") String max) {
        BigDecimal minPrice = new BigDecimal(min);
        BigDecimal maxPrice = new BigDecimal(max);
        return Product.where((Product p) -> p.price.compareTo(minPrice) >= 0 && p.price.compareTo(maxPrice) <= 0).toList();
    }

    // =============================================================================================
    // CATEGORY QUERIES
    // =============================================================================================

    @GET
    @Path("/category/{category}")
    public List<Product> getByCategory(@PathParam("category") String category) {
        return Product.where((Product p) -> p.category.equals(category)).toList();
    }

    @GET
    @Path("/category-contains/{text}")
    public List<Product> getCategoryContains(@PathParam("text") String text) {
        return Product.where((Product p) -> p.category.contains(text)).toList();
    }

    // =============================================================================================
    // LOGICAL OPERATIONS
    // =============================================================================================

    @GET
    @Path("/available-and-in-stock")
    public List<Product> getAvailableAndInStock() {
        return Product.where((Product p) -> p.available && p.stockQuantity > 0).toList();
    }

    @GET
    @Path("/available-or-low-price")
    public List<Product> getAvailableOrLowPrice() {
        return Product.where((Product p) -> p.available || p.price.compareTo(new BigDecimal("100")) < 0).toList();
    }

    // =============================================================================================
    // SORTING
    // =============================================================================================

    @GET
    @Path("/sorted-by-price")
    public List<Product> getSortedByPrice() {
        return Product.sortedBy((Product p) -> p.price).toList();
    }

    @GET
    @Path("/sorted-by-price-desc")
    public List<Product> getSortedByPriceDesc() {
        return Product.sortedDescendingBy((Product p) -> p.price).toList();
    }

    @GET
    @Path("/sorted-by-name")
    public List<Product> getSortedByName() {
        return Product.sortedBy((Product p) -> p.name).toList();
    }

    @GET
    @Path("/sorted-by-rating-desc")
    public List<Product> getSortedByRatingDesc() {
        return Product.where((Product p) -> p.rating != null)
                .sortedDescendingBy((Product p) -> p.rating)
                .toList();
    }

    // =============================================================================================
    // PROJECTIONS
    // =============================================================================================

    @GET
    @Path("/names")
    public List<String> getNames() {
        return Product.select((Product p) -> p.name).toList();
    }

    @GET
    @Path("/categories")
    public List<String> getCategories() {
        return Product.select((Product p) -> p.category).distinct().toList();
    }

    @GET
    @Path("/prices")
    public List<BigDecimal> getPrices() {
        return Product.select((Product p) -> p.price).toList();
    }

    // =============================================================================================
    // AGGREGATIONS
    // Aggregations must use getSingleResult(), not findFirst() - findFirst() goes through toList()
    // =============================================================================================

    @GET
    @Path("/min-price")
    public BigDecimal getMinPrice() {
        return Product.min((Product p) -> p.price).getSingleResult();
    }

    @GET
    @Path("/max-price")
    public BigDecimal getMaxPrice() {
        return Product.max((Product p) -> p.price).getSingleResult();
    }

    @GET
    @Path("/avg-rating")
    public Double getAvgRating() {
        return Product.where((Product p) -> p.rating != null)
                .avg((Product p) -> p.rating)
                .getSingleResult();
    }

    @GET
    @Path("/sum-stock")
    public Long getSumStock() {
        return Product.sumInteger((Product p) -> p.stockQuantity).getSingleResult();
    }

    // =============================================================================================
    // CHAINED OPERATIONS
    // =============================================================================================

    @GET
    @Path("/available-sorted-by-price")
    public List<Product> getAvailableSortedByPrice() {
        return Product.where((Product p) -> p.available)
                .sortedBy((Product p) -> p.price)
                .toList();
    }

    @GET
    @Path("/available-top/{count}")
    public List<Product> getAvailableTopByPrice(@PathParam("count") int count) {
        return Product.where((Product p) -> p.available)
                .sortedDescendingBy((Product p) -> p.price)
                .limit(count)
                .toList();
    }

    @GET
    @Path("/category/{category}/sorted-by-price")
    public List<Product> getCategorySortedByPrice(@PathParam("category") String category) {
        return Product.where((Product p) -> p.category.equals(category))
                .sortedBy((Product p) -> p.price)
                .toList();
    }

    // =============================================================================================
    // SINGLE RESULT OPERATIONS
    // =============================================================================================

    @GET
    @Path("/first-available")
    public Product getFirstAvailable() {
        return Product.where((Product p) -> p.available)
                .sortedBy((Product p) -> Long.valueOf(p.id))
                .findFirst()
                .orElse(null);
    }

    @GET
    @Path("/exists-category/{category}")
    public boolean existsInCategory(@PathParam("category") String category) {
        return Product.where((Product p) -> p.category.equals(category)).exists();
    }

    @GET
    @Path("/cheapest")
    public Product getCheapest() {
        return Product.sortedBy((Product p) -> p.price)
                .findFirst()
                .orElse(null);
    }

    @GET
    @Path("/most-expensive")
    public Product getMostExpensive() {
        return Product.sortedDescendingBy((Product p) -> p.price)
                .findFirst()
                .orElse(null);
    }
}
