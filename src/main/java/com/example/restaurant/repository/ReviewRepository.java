package com.example.restaurant.repository;

import com.example.restaurant.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Integer>, JpaSpecificationExecutor<Review> {
    long countByVisibleTrue();

    long countByVisibleFalse();

    long countByRating(Integer rating);

    long countByVisibleTrueAndRating(Integer rating);

    @Query("select avg(r.rating) from Review r")
    Double findAverageRating();

    @Query("select avg(r.rating) from Review r where r.visible = true")
    Double findVisibleAverageRating();
}
