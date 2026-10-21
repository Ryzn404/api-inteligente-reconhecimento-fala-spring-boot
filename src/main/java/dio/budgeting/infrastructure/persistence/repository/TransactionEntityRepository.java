package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionEntityRepository extends ListCrudRepository<TransactionEntity, UUID> {
    List<TransactionEntity> findAllByCategory(String category);
}
