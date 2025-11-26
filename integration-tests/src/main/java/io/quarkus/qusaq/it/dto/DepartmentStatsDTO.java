package io.quarkus.qusaq.it.dto;

import java.util.Objects;

/**
 * DTO for GROUP BY query results containing department statistics.
 * <p>
 * Used for testing GROUP BY queries with aggregations like:
 * {@code Person.groupBy(p -> p.department.name)
 *       .select(g -> new DepartmentStatsDTO(g.key(), g.count(), g.avg(p -> p.salary)))}
 */
public class DepartmentStatsDTO {

    private final String departmentName;
    private final long employeeCount;
    private final Double averageSalary;

    public DepartmentStatsDTO(String departmentName, long employeeCount, Double averageSalary) {
        this.departmentName = departmentName;
        this.employeeCount = employeeCount;
        this.averageSalary = averageSalary;
    }

    /**
     * Constructor for key + count projections.
     */
    public DepartmentStatsDTO(String departmentName, long employeeCount) {
        this(departmentName, employeeCount, null);
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public long getEmployeeCount() {
        return employeeCount;
    }

    public Double getAverageSalary() {
        return averageSalary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DepartmentStatsDTO that)) return false;
        return employeeCount == that.employeeCount &&
               Objects.equals(departmentName, that.departmentName) &&
               Objects.equals(averageSalary, that.averageSalary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(departmentName, employeeCount, averageSalary);
    }

    @Override
    public String toString() {
        return "DepartmentStatsDTO{" +
               "departmentName='" + departmentName + '\'' +
               ", employeeCount=" + employeeCount +
               ", averageSalary=" + averageSalary +
               '}';
    }
}
