package com.carwash.shared.application;

/** Owned repositories implement a bounded query; internal complete-set workflows stay separate. */
public interface PagedRepository<T> {
    default PageResult<T> findPage(ListQuery query) { throw new UnsupportedOperationException("Paged query not implemented"); }
}
