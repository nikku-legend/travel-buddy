package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {

    Optional<City> findBySlug(String slug);

    List<City> findByState_StateIdOrderByNameAsc(
            Integer stateId
    );

    /**
     * Name search for the city picker. Bounded by LIMIT in the
     * service rather than here, because a caller-supplied limit
     * in a derived query is easy to forget.
     */
    @Query("""
            select c from City c
            where lower(c.name) like lower(concat('%', :term, '%'))
            order by c.name
            """)
    List<City> search(@Param("term") String term);
}