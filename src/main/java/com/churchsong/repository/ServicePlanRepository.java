package com.churchsong.repository;

import com.churchsong.model.ServicePlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServicePlanRepository
        extends JpaRepository<ServicePlan, Long> {
}
