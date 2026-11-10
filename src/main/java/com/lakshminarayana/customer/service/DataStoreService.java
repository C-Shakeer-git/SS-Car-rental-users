package com.lakshminarayana.customer.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lakshminarayana.customer.model.Booking;
import com.lakshminarayana.customer.model.Car;
import com.lakshminarayana.customer.model.Customer;
import com.lakshminarayana.customer.model.Payment;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DataStoreService {

    private static final Logger log = LoggerFactory.getLogger(DataStoreService.class);

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Autowired
    private GoogleSheetsService googleSheetsService;

    @Autowired
    private DataSeeder dataSeeder;

    @Value("${data.uploads.dir:./uploads}")
    private String uploadsDir;

    private final Map<String, Car> carsMap = new ConcurrentHashMap<>();
    private final Map<String, Customer> customersMap = new ConcurrentHashMap<>();
    private final Map<String, Booking> bookingsMap = new ConcurrentHashMap<>();
    private final List<Payment> paymentsList = Collections.synchronizedList(new ArrayList<>());

    @PostConstruct
    public void init() {
        // Ensure uploads directory exists
        new File(uploadsDir).mkdirs();
        // Load hardcoded seed data
        seedAllData();
        log.info("Data initialized from DataSeeder — {} cars, {} customers, {} bookings, {} payments",
                carsMap.size(), customersMap.size(), bookingsMap.size(), paymentsList.size());
    }

    /**
     * Populate all in-memory data stores from the hardcoded DataSeeder.
     */
    private void seedAllData() {
        // Cars
        for (Car c : dataSeeder.getInitialCars()) {
            carsMap.put(c.getId(), c);
        }
        // Customers
        for (Customer c : dataSeeder.getInitialCustomers()) {
            customersMap.put(c.getId(), c);
        }
        // Bookings
        for (Booking b : dataSeeder.getInitialBookings()) {
            bookingsMap.put(b.getId(), b);
        }
        // Payments
        paymentsList.addAll(dataSeeder.getInitialPayments());
    }

    // ═══════════════════════════════════════════════════════════
    //  CARS
    // ═══════════════════════════════════════════════════════════

    public List<Car> getAllCars() {
        return new ArrayList<>(carsMap.values());
    }

    public Optional<Car> getCarById(String id) {
        return Optional.ofNullable(carsMap.get(id));
    }

    public synchronized void saveCar(Car car) {
        carsMap.put(car.getId(), car);
        if (googleSheetsService.isConnected()) {
            List<Object> row = Arrays.asList(
                    car.getId(), car.getName(), car.getBrand(), car.getCategory(),
                    car.getSeaters(), car.getFuelType(), car.getTransmission(),
                    car.getPricePerDay(), car.getImageUrl(), car.getFeaturesAsString(),
                    car.getMileage(), car.getLuggageCapacity(), car.getRegistrationNumber(),
                    car.isAvailable(), car.getRating(), car.getTripsCompleted()
            );
            googleSheetsService.appendRow("Cars!A:P", row);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  CUSTOMERS
    // ═══════════════════════════════════════════════════════════

    public List<Customer> getAllCustomers() {
        return new ArrayList<>(customersMap.values());
    }

    public Optional<Customer> getCustomerById(String id) {
        return Optional.ofNullable(customersMap.get(id));
    }

    public Optional<Customer> getCustomerByEmail(String email) {
        if (email == null) return Optional.empty();
        return customersMap.values().stream()
                .filter(c -> c.getEmail().trim().equalsIgnoreCase(email.trim()))
                .findFirst();
    }

    public synchronized Customer saveCustomer(Customer customer) {
        if (customer.getId() == null || customer.getId().isBlank()) {
            customer.setId("CUST-" + (1000 + customersMap.size() + 1));
        }
        if (customer.getCreatedAt() == null) {
            customer.setCreatedAt(LocalDateTime.now().toString());
        }
        customersMap.put(customer.getId(), customer);

        if (googleSheetsService.isConnected()) {
            List<Object> row = Arrays.asList(
                    customer.getId(), customer.getFullName(), customer.getEmail(),
                    customer.getPassword(), customer.getPhone(), customer.getAddress(),
                    customer.getAadhaarNumber(), customer.getAadhaarDocUrl(),
                    customer.getDrivingLicenseNumber(), customer.getDrivingLicenseDocUrl(),
                    customer.getKycStatus(), customer.getKycRemarks(), customer.getCreatedAt()
            );
            googleSheetsService.appendRow("Customers!A:M", row);
        }
        return customer;
    }

    // ═══════════════════════════════════════════════════════════
    //  BOOKINGS
    // ═══════════════════════════════════════════════════════════

    public List<Booking> getAllBookings() {
        return bookingsMap.values().stream()
                .sorted((b1, b2) -> Objects.toString(b2.getCreatedAt(), "").compareTo(Objects.toString(b1.getCreatedAt(), "")))
                .collect(Collectors.toList());
    }

    public Optional<Booking> getBookingById(String id) {
        return Optional.ofNullable(bookingsMap.get(id));
    }

    public List<Booking> getBookingsByCustomerId(String customerId) {
        return bookingsMap.values().stream()
                .filter(b -> customerId != null && customerId.equals(b.getCustomerId()))
                .sorted((b1, b2) -> Objects.toString(b2.getCreatedAt(), "").compareTo(Objects.toString(b1.getCreatedAt(), "")))
                .collect(Collectors.toList());
    }

    public synchronized Booking saveBooking(Booking booking) {
        if (booking.getId() == null || booking.getId().isBlank()) {
            int seq = 100 + bookingsMap.size() + 1;
            booking.setId("LNT-BK-2026-" + seq);
        }
        if (booking.getInvoiceNumber() == null || booking.getInvoiceNumber().isBlank()) {
            booking.setInvoiceNumber("INV-" + booking.getId());
        }
        if (booking.getCreatedAt() == null) {
            booking.setCreatedAt(LocalDateTime.now().toString());
        }
        bookingsMap.put(booking.getId(), booking);

        if (googleSheetsService.isConnected()) {
            List<Object> row = Arrays.asList(
                    booking.getId(), booking.getCustomerId(), booking.getCustomerName(),
                    booking.getCustomerEmail(), booking.getCarId(), booking.getCarName(),
                    booking.getPickupDate(), booking.getDropDate(), booking.getPickupLocation(),
                    booking.getDropLocation(), booking.getTotalDays(), booking.getDailyRate(),
                    booking.getSelectedFeaturesAsString(), booking.getBaseAmount(),
                    booking.getAddOnAmount(), booking.getGstAmount(), booking.getTotalAmount(),
                    booking.getStatus(), booking.getPaymentStatus(), booking.getPaymentMethod(),
                    booking.getTransactionId(), booking.getCreatedAt()
            );
            googleSheetsService.appendRow("Bookings!A:V", row);
        }
        return booking;
    }

    // ═══════════════════════════════════════════════════════════
    //  PAYMENTS
    // ═══════════════════════════════════════════════════════════

    public List<Payment> getAllPayments() {
        return new ArrayList<>(paymentsList);
    }

    public synchronized Payment recordPayment(Payment payment) {
        if (payment.getId() == null || payment.getId().isBlank()) {
            payment.setId("PAY-" + (1000 + paymentsList.size() + 1));
        }
        if (payment.getPaymentDate() == null) {
            payment.setPaymentDate(LocalDateTime.now().toString());
        }
        paymentsList.add(payment);

        if (googleSheetsService.isConnected()) {
            List<Object> row = Arrays.asList(
                    payment.getId(), payment.getBookingId(), payment.getCustomerName(),
                    payment.getAmount(), payment.getPaymentMethod(), payment.getTransactionRef(),
                    payment.getPaymentDate(), payment.getStatus()
            );
            googleSheetsService.appendRow("Payments!A:H", row);
        }
        return payment;
    }

    // ═══════════════════════════════════════════════════════════
    //  FILE UPLOADS (for proof documents only)
    // ═══════════════════════════════════════════════════════════

    public String saveProofFile(String originalFileName, byte[] bytes) throws IOException {
        String ext = "";
        int dot = originalFileName.lastIndexOf('.');
        if (dot > 0) ext = originalFileName.substring(dot);
        String savedName = UUID.randomUUID().toString() + ext;
        Path uploadPath = Paths.get(uploadsDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        Path target = uploadPath.resolve(savedName);
        Files.write(target, bytes);
        return "/uploads/" + savedName;
    }
}
