package com.example.learning.config;

import com.example.learning.repository.refresh.CustomRepository;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;


public class CustomRepositoryConfig <T, ID extends Serializable> extends SimpleJpaRepository<T, ID>
        implements CustomRepository<T, ID> {
    private final EntityManager entityManager;

    public CustomRepositoryConfig(JpaEntityInformation entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void refresh(T t) {
        entityManager.refresh(t);
    }

    @Override
    @Transactional
    public void refreshList(List<T> t) {
        for (T each : t) {
            entityManager.refresh(each);
        }

    }

    @Override
    public List<T> findTest (Class<T> clazz) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = builder.createQuery(clazz);
        Root<T> root = query.from(clazz);
        System.out.println(entityManager.getProperties());

        return entityManager.createQuery(query.select(root)).setFirstResult(1).setMaxResults(5).getResultList();
    }

    @Override
    public List<T> findSpecAll(Specification<T> spec, int offset, int maxResults, Sort sort, Class <T> clazz, String [] entityGraphList) {
        TypedQuery<T> query = getQuery(spec,sort);
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must not be less than zero!");
        }
        if (maxResults < 1) {
            throw new IllegalArgumentException("Max results must not be less than one!");
        }
        EntityGraph entityGraph = (EntityGraph) entityManager.createEntityGraph(clazz);
        entityGraph.addAttributeNodes("boxesTotal");

        query.setHint("jakarta.persistence.fetchgraph", entityGraph);
        query.setFirstResult(offset).setMaxResults(maxResults);

        return query.getResultList();
    }
}
