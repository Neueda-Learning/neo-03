package com.neobank.module.repository;

import com.neobank.module.model.DemoShowcase;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data writes the implementation from these method names — the same {@code findBy…} /
 * {@code findAllOrderBy…} derivation you used in Week 2.
 *
 * <p>⚠️ Placeholder, like the entity it serves. Your repository will be for <em>your</em> table;
 * see {@link DemoShowcase} for how to replace it.</p>
 */
public interface DemoShowcaseRepository extends JpaRepository<DemoShowcase, Long> {

    /**
     * Newest first — what the board shows.
     *
     * <p><b>Why the {@code id} tiebreak.</b> MySQL {@code TIMESTAMP} stores whole seconds unless you
     * ask for fractions, so several applications processed in the same second carry the <em>same</em>
     * {@code created_at}. Ordering by the timestamp alone then leaves their relative order up to the
     * database, and the board shuffles them between refreshes. The auto-increment id is monotonic, so
     * it settles ties. (Found by {@code DemoShowcaseRepositoryIT} — H2 could not have shown it.)</p>
     */
    List<DemoShowcase> findAllByOrderByCreatedAtDescIdDesc();
}
