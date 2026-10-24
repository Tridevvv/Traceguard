package com.traceguard.policy;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/** The approved checkpoint order and time limits for one product type. */
@Entity
@Table(name = "route_policies")
public class RoutePolicy {

    /** One approved stop. {@code maxHoursFromPrevious} limits the time since the previous stop (null = no limit). */
    @Embeddable
    public static class Step {
        @Column(name = "checkpoint_id", nullable = false)
        private Long checkpointId;

        @Column(name = "max_hours_from_previous")
        private Integer maxHoursFromPrevious;

        protected Step() {}

        public Step(Long checkpointId, Integer maxHoursFromPrevious) {
            this.checkpointId = checkpointId;
            this.maxHoursFromPrevious = maxHoursFromPrevious;
        }

        public Long getCheckpointId() { return checkpointId; }
        public Integer getMaxHoursFromPrevious() { return maxHoursFromPrevious; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_type", nullable = false, unique = true, length = 100)
    private String productType;

    @Column(name = "max_total_hours", nullable = false)
    private int maxTotalHours;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "route_policy_steps", joinColumns = @JoinColumn(name = "policy_id"))
    @OrderColumn(name = "step_order")
    private List<Step> steps = new ArrayList<>();

    protected RoutePolicy() {}

    public RoutePolicy(String productType, int maxTotalHours, List<Step> steps) {
        this.productType = productType;
        this.maxTotalHours = maxTotalHours;
        this.steps = new ArrayList<>(steps);
    }

    public void replace(int maxTotalHours, List<Step> steps) {
        this.maxTotalHours = maxTotalHours;
        this.steps.clear();
        this.steps.addAll(steps);
    }

    public Long getId() { return id; }
    public String getProductType() { return productType; }
    public int getMaxTotalHours() { return maxTotalHours; }
    public List<Step> getSteps() { return steps; }
}
