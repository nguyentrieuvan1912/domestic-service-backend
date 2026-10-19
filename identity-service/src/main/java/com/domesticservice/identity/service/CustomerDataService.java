package com.domesticservice.identity.service;

import com.domesticservice.identity.dto.AccountResponse.User;
import com.domesticservice.identity.dto.CustomerRequests.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static com.domesticservice.identity.service.AccountService.error;
import com.domesticservice.identity.repository.CustomerDataSqlRepository;

@Service
@Transactional
public class CustomerDataService {

    private final CustomerDataSqlRepository repository;

    private final ObjectMapper json;

    public CustomerDataService(CustomerDataSqlRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    private void customer(User user) {
        if (!"CUSTOMER".equals(user.role()))
            throw error(HttpStatus.FORBIDDEN, "Vui lòng sử dụng tài khoản khách hàng.");
    }

    private void lockCustomer(User user) {
        customer(user);
        repository.lockCustomerFindCustomersForUpdate(Long.class, user.id());
    }

    public List<Map<String, Object>> addresses(User user) {
        customer(user);
        return repository.addressesFindAddresses(user.id());
    }

    public Map<String, Object> address(User user, long id) {
        customer(user);
        var values = repository.addressFindAddresses(id, user.id());
        if (values.isEmpty())
            throw error(HttpStatus.NOT_FOUND, "Không tìm thấy địa chỉ của bạn.");
        return values.getFirst();
    }

    public Map<String, Object> saveAddress(User user, Long id, Address body) {
        lockCustomer(user);
        var existing = id == null ? null : address(user, id);
        String district = Objects.toString(body.district(), "").strip();
        String ward = java.text.Normalizer.normalize(Objects.toString(body.ward(), "").strip(), java.text.Normalizer.Form.NFC);
        String province = body.city().strip();
        if (district.isBlank()) {
            if (ward.isBlank())
                throw error(HttpStatus.BAD_REQUEST, "Vui lòng chọn phường/xã.");
            if (!List.of("TP. Hồ Chí Minh", "Thành phố Hồ Chí Minh", "Hồ Chí Minh").contains(province))
                throw error(HttpStatus.BAD_REQUEST, "Danh mục phường/xã hiện hỗ trợ TP. Hồ Chí Minh.");
            province = "TP. Hồ Chí Minh";
            if (!Boolean.TRUE.equals(repository.saveAddressExistsAdministrativeUnits(Boolean.class, province, ward)))
                throw error(HttpStatus.BAD_REQUEST, "Vui lòng chọn phường/xã hợp lệ trong danh sách TP. Hồ Chí Minh.");
        }
        final String savedProvince = province;
        boolean isDefault = Boolean.TRUE.equals(body.isDefault()) || (existing != null && Boolean.TRUE.equals(existing.get("isDefault"))) || !Boolean.TRUE.equals(repository.saveAddressExistsAddresses(Boolean.class, user.id()));
        if (isDefault)
            repository.saveAddressUpdateAddresses(user.id());
        String phone = body.recipientPhone().startsWith("+84") ? "0" + body.recipientPhone().substring(3) : body.recipientPhone();
        if (id == null) {
            id = repository.saveAddressInsertAddresses(user.id(), body.title().strip(), body.recipientName().strip(), phone, savedProvince, district, ward, body.streetAddress().strip(), Objects.toString(body.note(), "").strip(), isDefault);
        } else
            repository.saveAddressUpdateAddresses2(body.title().strip(), body.recipientName().strip(), phone, savedProvince, district, ward, body.streetAddress().strip(), Objects.toString(body.note(), "").strip(), isDefault, id, user.id());
        return address(user, id);
    }

    public Map<String, Object> setDefault(User user, long id) {
        lockCustomer(user);
        address(user, id);
        repository.setDefaultUpdateAddresses(user.id());
        repository.setDefaultUpdateAddresses2(id, user.id());
        return address(user, id);
    }

    public void deleteAddress(User user, long id) {
        lockCustomer(user);
        address(user, id);
        repository.deleteAddressDeleteAddresses(id, user.id());
        if (!Boolean.TRUE.equals(repository.deleteAddressExistsAddresses(Boolean.class, user.id()))) {
            var next = repository.deleteAddressFindAddresses(Long.class, user.id());
            if (!next.isEmpty())
                repository.deleteAddressUpdateAddresses(next.getFirst());
        }
    }

    public List<Map<String, Object>> notifications(User user) {
        return repository.notificationsFindNotifications(user, user.id());
    }

    public Map<String, Object> notification(User user, long id) {
        var values = repository.notificationFindNotifications(user, id, user.id());
        if (values.isEmpty())
            throw error(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo của bạn.");
        return values.getFirst();
    }

    public Map<String, Integer> markRead(User user, Long id) {
        if (id != null)
            notification(user, id);
        int changed = id == null ? repository.markReadUpdateNotifications(user.id()) : repository.markReadUpdateNotifications2(id, user.id());
        return Map.of("changed", changed);
    }

    public Map<String, Object> contact(Contact body) {
        long id = repository.contactInsertSupportRequests(body.name().strip(), body.email().strip().toLowerCase(Locale.ROOT), body.subject().strip(), body.message().strip());
        return Map.of("id", id, "status", "RECEIVED");
    }
}
