package com.example.learning.repository.refresh;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;
import java.util.List;

@NoRepositoryBean
public interface CustomRepository<T,ID extends Serializable> extends JpaRepository<T,ID> {
    void refresh(T t);
    void refreshList(List<T> t);
    List<T> findSpecAll(Specification<T> spec, int offset, int maxResults, Sort sort, Class <T> clazz, String [] entityGraphList);
    List<T> findTest(Class <T> clazz);
}
