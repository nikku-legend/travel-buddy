package com.Travel.Buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A published house policy. (SRS 2.3 section 6.2)
 *
 * <p>Check-in and check-out times are the two facts a traveller
 * most needs and can derive least from the rest of the page,
 * which is why they are modelled as policies rather than
 * columns on the property row.
 */
@Entity
@Table(name = "property_policies")
public class PropertyPolicy {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long policyId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "property_id",
            nullable = false
    )
    private Property property;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "policy_type",
            nullable = false
    )
    private PolicyType policyType;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    protected PropertyPolicy() {
    }

    public PropertyPolicy(
            Property property,
            PolicyType policyType,
            String title,
            String description,
            int sortOrder
    ) {
        this.property = property;
        this.policyType = policyType;
        this.title = title;
        this.description = description;
        this.sortOrder = sortOrder;
    }


    /**
     * Rewrites the policy in place, keeping its id so anything
     * that referenced it still resolves.
     */
    public void replace(
            PolicyType policyType,
            String title,
            String description,
            int sortOrder
    ) {
        this.policyType = policyType;
        this.title = title;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public Long getPolicyId() {
        return policyId;
    }

    public PolicyType getPolicyType() {
        return policyType;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}