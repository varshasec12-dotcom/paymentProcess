package com.bank.payments.repository;

import com.bank.payments.model.ContactChangeRequest;
import com.bank.payments.model.ContactChangeRequest.ContactType;
import com.bank.payments.model.ContactChangeRequest.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContactChangeRequestRepository extends JpaRepository<ContactChangeRequest, Long> {

    List<ContactChangeRequest> findByCustomer_CustomerIdAndContactTypeAndStatus(
            Long customerId, ContactType contactType, RequestStatus status);

    Optional<ContactChangeRequest> findByRequestIdAndCustomer_CustomerId(Long requestId, Long customerId);
}
