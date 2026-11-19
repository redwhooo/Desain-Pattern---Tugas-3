import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class JemberEats {
    private static final AtomicInteger ORDER_COUNTER = new AtomicInteger(1000);

    static String money(double amount) {
        return String.format(Locale.forLanguageTag("id-ID"), "Rp%,.0f", amount);
    }

    enum ServiceType {
        DINE_IN, DELIVERY, PICKUP
    }

    enum OrderAction {
        CONFIRM, START_PREPARING, MARK_READY, DISPATCH, COMPLETE, CANCEL
    }

    static class Customer {
        final String name;
        private double balance;

        Customer(String name, double balance) {
            this.name = name;
            this.balance = balance;
        }

        double getBalance() {
            return balance;
        }

        void deductBalance(double amount) {
            if (amount > balance) {
                throw new IllegalStateException("Saldo pelanggan tidak mencukupi.");
            }
            balance -= amount;
        }
    }

    static class Restaurant {
        final String name;
        boolean open;

        Restaurant(String name, boolean open) {
            this.name = name;
            this.open = open;
        }
    }

    static class MenuItem {
        final String name;
        final double price;
        boolean available;

        MenuItem(String name, double price, boolean available) {
            this.name = name;
            this.price = price;
            this.available = available;
        }
    }

    static class CartItem {
        final MenuItem menuItem;
        final int quantity;

        CartItem(MenuItem menuItem, int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Jumlah item harus lebih dari 0.");
            }
            this.menuItem = menuItem;
            this.quantity = quantity;
        }

        double getSubtotal() {
            return menuItem.price * quantity;
        }

        CartItem copy() {
            return new CartItem(menuItem, quantity);
        }
    }

    // MEMENTO: menyimpan dan memulihkan kondisi keranjang
    static class Cart {
        private final List<CartItem> items = new ArrayList<>();

        void add(MenuItem menuItem, int quantity) {
            items.add(new CartItem(menuItem, quantity));
        }

        List<CartItem> getItems() {
            List<CartItem> copy = new ArrayList<>();
            for (CartItem item : items) {
                copy.add(item.copy());
            }
            return copy;
        }

        double getSubtotal() {
            double total = 0;
            for (CartItem item : items) {
                total += item.getSubtotal();
            }
            return total;
        }

        CartMemento save() {
            return new CartMemento(getItems());
        }

        void restore(CartMemento memento) {
            items.clear();
            for (CartItem item : memento.savedItems) {
                items.add(item.copy());
            }
        }
    }

    // Memento hanya menyimpan snapshot; isinya tidak diubah dari luar
    static class CartMemento {
        private final List<CartItem> savedItems;

        private CartMemento(List<CartItem> items) {
            savedItems = new ArrayList<>();
            for (CartItem item : items) {
                savedItems.add(item.copy());
            }
        }
    }

    // Caretaker yang menyimpan snapshot tanpa mengubah detail isi Memento
    static class CartHistory {
        private CartMemento lastSaved;

        void backup(Cart cart) {
            lastSaved = cart.save();
        }

        void restore(Cart cart) {
            if (lastSaved == null) {
                throw new IllegalStateException("Belum ada snapshot keranjang.");
            }
            cart.restore(lastSaved);
        }
    }

    // OBSERVER: pelanggan, restoran, dan kurir menerima perubahan status
    interface OrderObserver {
        void update(Order order, String newStatus);
    }

    static class CustomerNotifier implements OrderObserver {
        public void update(Order order, String newStatus) {
            System.out.println("[Notifikasi Pelanggan] " + order.customer.name
                    + ", pesanan " + order.id + " sekarang: " + newStatus);
        }
    }

    static class RestaurantNotifier implements OrderObserver {
        public void update(Order order, String newStatus) {
            System.out.println("[Notifikasi Restoran] " + order.restaurant.name
                    + " menerima pembaruan pesanan " + order.id + ": " + newStatus);
        }
    }

    static class CourierNotifier implements OrderObserver {
        public void update(Order order, String newStatus) {
            System.out.println("[Notifikasi Kurir] Pembaruan pesanan " + order.id + ": " + newStatus);
        }
    }

    // STATE: perilaku Order berubah mengikuti statusnya
    interface OrderState {
        String getStatus();

        void handle(Order order, OrderAction action);
    }

    abstract static class BaseOrderState implements OrderState {
        protected void reject(Order order, OrderAction action) {
            throw new IllegalStateException("Aksi " + action + " tidak valid saat pesanan "
                    + order.getStatus() + ".");
        }
    }

    static class CreatedState extends BaseOrderState {
        public String getStatus() {
            return "DIBUAT";
        }

        public void handle(Order order, OrderAction action) {
            if (action == OrderAction.CONFIRM) {
                order.changeState(new ConfirmedState());
            } else if (action == OrderAction.CANCEL) {
                order.changeState(new CanceledState());
            } else {
                reject(order, action);
            }
        }
    }

    static class ConfirmedState extends BaseOrderState {
        public String getStatus() {
            return "DIKONFIRMASI";
        }

        public void handle(Order order, OrderAction action) {
            if (action == OrderAction.START_PREPARING) {
                order.changeState(new PreparingState());
            } else if (action == OrderAction.CANCEL) {
                order.changeState(new CanceledState());
            } else {
                reject(order, action);
            }
        }
    }

    static class PreparingState extends BaseOrderState {
        public String getStatus() {
            return "SEDANG_DISIAPKAN";
        }

        public void handle(Order order, OrderAction action) {
            if (action == OrderAction.MARK_READY) {
                order.changeState(new ReadyState());
            } else {
                reject(order, action);
            }
        }
    }

    static class ReadyState extends BaseOrderState {
        public String getStatus() {
            return "SIAP";
        }

        public void handle(Order order, OrderAction action) {
            if (action == OrderAction.DISPATCH && order.serviceType == ServiceType.DELIVERY) {
                order.changeState(new OnDeliveryState());
            } else if (action == OrderAction.COMPLETE && order.serviceType != ServiceType.DELIVERY) {
                order.changeState(new CompletedState());
            } else {
                reject(order, action);
            }
        }
    }

    static class OnDeliveryState extends BaseOrderState {
        public String getStatus() {
            return "DALAM_PENGANTARAN";
        }

        public void handle(Order order, OrderAction action) {
            if (action == OrderAction.COMPLETE) {
                order.changeState(new CompletedState());
            } else {
                reject(order, action);
            }
        }
    }

    static class CompletedState extends BaseOrderState {
        public String getStatus() {
            return "SELESAI";
        }

        public void handle(Order order, OrderAction action) {
            reject(order, action);
        }
    }

    static class CanceledState extends BaseOrderState {
        public String getStatus() {
            return "DIBATALKAN";
        }

        public void handle(Order order, OrderAction action) {
            reject(order, action);
        }
    }

    static class Order {
        final String id;
        final Customer customer;
        final Restaurant restaurant;
        final List<CartItem> items;
        final double total;
        final ServiceType serviceType;
        private OrderState state = new CreatedState();
        private final List<OrderObserver> observers = new ArrayList<>();

        Order(Customer customer, Restaurant restaurant, List<CartItem> items,
                double total, ServiceType serviceType) {
            this.id = "JE-" + ORDER_COUNTER.incrementAndGet();
            this.customer = customer;
            this.restaurant = restaurant;
            this.items = new ArrayList<>();
            for (CartItem item : items) {
                this.items.add(item.copy());
            }
            this.total = total;
            this.serviceType = serviceType;
        }

        void addObserver(OrderObserver observer) {
            observers.add(observer);
        }

        String getStatus() {
            return state.getStatus();
        }

        void changeState(OrderState newState) {
            state = newState;
            notifyObservers();
        }

        private void notifyObservers() {
            for (OrderObserver observer : observers) {
                observer.update(this, getStatus());
            }
        }

        void confirm() {
            state.handle(this, OrderAction.CONFIRM);
        }

        void startPreparing() {
            state.handle(this, OrderAction.START_PREPARING);
        }

        void markReady() {
            state.handle(this, OrderAction.MARK_READY);
        }

        void dispatch() {
            state.handle(this, OrderAction.DISPATCH);
        }

        void complete() {
            state.handle(this, OrderAction.COMPLETE);
        }

        void cancel() {
            state.handle(this, OrderAction.CANCEL);
        }
    }

    // STRATEGY: perhitungan ongkir, diskon, dan pembayaran dapat diganti
    interface ShippingFeeStrategy {
        double calculateFee(double distanceKm);
    }

    static class PerKmShippingFee implements ShippingFeeStrategy {
        private final double feePerKm;

        PerKmShippingFee(double feePerKm) {
            this.feePerKm = feePerKm;
        }

        public double calculateFee(double distanceKm) {
            return feePerKm * distanceKm;
        }
    }

    static class FreeShippingFee implements ShippingFeeStrategy {
        public double calculateFee(double distanceKm) {
            return 0;
        }
    }

    interface DiscountStrategy {
        double calculateDiscount(double subtotal, String promoCode);
    }

    static class NoDiscountStrategy implements DiscountStrategy {
        public double calculateDiscount(double subtotal, String promoCode) {
            return 0;
        }
    }

    static class PercentageDiscountStrategy implements DiscountStrategy {
        private final double percentage;

        PercentageDiscountStrategy(double percentage) {
            this.percentage = percentage;
        }

        public double calculateDiscount(double subtotal, String promoCode) {
            if ("HEMAT10".equalsIgnoreCase(promoCode)) {
                return subtotal * percentage;
            }
            return 0;
        }
    }

    interface PaymentStrategy {
        boolean requiresBalance();

        String getName();

        void pay(Customer customer, double amount);
    }

    static class WalletPaymentStrategy implements PaymentStrategy {
        public boolean requiresBalance() {
            return true;
        }

        public String getName() {
            return "Saldo JemberEats";
        }

        public void pay(Customer customer, double amount) {
            customer.deductBalance(amount);
            System.out.println("Pembayaran " + money(amount) + " berhasil melalui saldo.");
        }
    }

    static class CashOnDeliveryStrategy implements PaymentStrategy {
        public boolean requiresBalance() {
            return false;
        }

        public String getName() {
            return "Bayar di Tempat (COD)";
        }

        public void pay(Customer customer, double amount) {
            System.out.println("Pembayaran " + money(amount) + " akan dilakukan secara COD.");
        }
    }

    // CHAIN OF RESPONSIBILITY: validasi dijalankan secara berurutan
    static class CheckoutRequest {
        final Customer customer;
        final Restaurant restaurant;
        final List<CartItem> items;
        final ServiceType serviceType;
        final double distanceKm;
        final String promoCode;
        final ShippingFeeStrategy shippingStrategy;
        final DiscountStrategy discountStrategy;
        final PaymentStrategy paymentStrategy;
        final List<OrderObserver> observers;

        CheckoutRequest(Customer customer, Restaurant restaurant, List<CartItem> items,
                ServiceType serviceType, double distanceKm, String promoCode,
                ShippingFeeStrategy shippingStrategy, DiscountStrategy discountStrategy,
                PaymentStrategy paymentStrategy, List<OrderObserver> observers) {
            this.customer = customer;
            this.restaurant = restaurant;
            this.items = new ArrayList<>();
            for (CartItem item : items) {
                this.items.add(item.copy());
            }
            this.serviceType = serviceType;
            this.distanceKm = distanceKm;
            this.promoCode = promoCode;
            this.shippingStrategy = shippingStrategy;
            this.discountStrategy = discountStrategy;
            this.paymentStrategy = paymentStrategy;
            this.observers = new ArrayList<>(observers);
        }
    }

    static class ValidationContext {
        final CheckoutRequest request;
        final double total;

        ValidationContext(CheckoutRequest request, double total) {
            this.request = request;
            this.total = total;
        }
    }

    abstract static class ValidationHandler {
        private ValidationHandler next;

        ValidationHandler linkWith(ValidationHandler nextHandler) {
            this.next = nextHandler;
            return nextHandler;
        }

        final void handle(ValidationContext context) {
            validate(context);
            if (next != null) {
                next.handle(context);
            }
        }

        protected abstract void validate(ValidationContext context);
    }

    static class MenuAvailabilityValidator extends ValidationHandler {
        protected void validate(ValidationContext context) {
            if (context.request.items.isEmpty()) {
                throw new IllegalStateException("Keranjang masih kosong.");
            }
            for (CartItem item : context.request.items) {
                if (!item.menuItem.available) {
                    throw new IllegalStateException("Menu tidak tersedia: " + item.menuItem.name);
                }
            }
            System.out.println("Validasi menu: berhasil.");
        }
    }

    static class RestaurantOpenValidator extends ValidationHandler {
        protected void validate(ValidationContext context) {
            if (!context.request.restaurant.open) {
                throw new IllegalStateException("Restoran sedang tutup.");
            }
            System.out.println("Validasi jam operasional restoran: berhasil.");
        }
    }

    static class PromoValidator extends ValidationHandler {
        protected void validate(ValidationContext context) {
            String promo = context.request.promoCode;
            if (promo != null && !promo.trim().isEmpty()
                    && !"HEMAT10".equalsIgnoreCase(promo)) {
                throw new IllegalStateException("Kode promo tidak valid.");
            }
            System.out.println("Validasi kode promo: berhasil.");
        }
    }

    static class BalanceValidator extends ValidationHandler {
        protected void validate(ValidationContext context) {
            if (context.request.paymentStrategy.requiresBalance()
                    && context.request.customer.getBalance() < context.total) {
                throw new IllegalStateException("Saldo tidak mencukupi. Total: " + money(context.total));
            }
            System.out.println("Validasi saldo/metode pembayaran: berhasil.");
        }
    }

    static ValidationHandler createValidationChain() {
        ValidationHandler menu = new MenuAvailabilityValidator();
        menu.linkWith(new RestaurantOpenValidator())
                .linkWith(new PromoValidator())
                .linkWith(new BalanceValidator());
        return menu;
    }

    // TEMPLATE METHOD: urutan checkout tetap, tahap pemenuhan berbeda
    abstract static class OrderProcessingTemplate {
        private final ValidationHandler validationChain;

        OrderProcessingTemplate(ValidationHandler validationChain) {
            this.validationChain = validationChain;
        }

        public final Order process(CheckoutRequest request) {
            System.out.println("\n=== Memulai checkout " + request.serviceType + " ===");

            double subtotal = calculateSubtotal(request.items);
            double shippingFee = request.serviceType == ServiceType.DELIVERY
                    ? request.shippingStrategy.calculateFee(request.distanceKm)
                    : 0;
            double discount = request.discountStrategy.calculateDiscount(subtotal, request.promoCode);
            double total = subtotal + shippingFee - discount;

            System.out.println("Subtotal: " + money(subtotal));
            System.out.println("Ongkir: " + money(shippingFee));
            System.out.println("Diskon: " + money(discount));
            System.out.println("Total: " + money(total));

            validationChain.handle(new ValidationContext(request, total));

            request.paymentStrategy.pay(request.customer, total);

            Order order = new Order(request.customer, request.restaurant,
                    request.items, total, request.serviceType);
            for (OrderObserver observer : request.observers) {
                order.addObserver(observer);
            }

            order.confirm();
            order.startPreparing();
            order.markReady();

            fulfillOrder(order);
            return order;
        }

        private double calculateSubtotal(List<CartItem> items) {
            double subtotal = 0;
            for (CartItem item : items) {
                subtotal += item.getSubtotal();
            }
            return subtotal;
        }

        protected abstract void fulfillOrder(Order order);
    }

    static class DeliveryOrderProcessor extends OrderProcessingTemplate {
        DeliveryOrderProcessor(ValidationHandler chain) {
            super(chain);
        }

        protected void fulfillOrder(Order order) {
            order.dispatch();
        }
    }

    static class PickupOrderProcessor extends OrderProcessingTemplate {
        PickupOrderProcessor(ValidationHandler chain) {
            super(chain);
        }

        protected void fulfillOrder(Order order) {
            order.complete();
        }
    }

    static class DineInOrderProcessor extends OrderProcessingTemplate {
        DineInOrderProcessor(ValidationHandler chain) {
            super(chain);
        }

        protected void fulfillOrder(Order order) {
            order.complete();
        }
    }

    // COMMAND: aksi pembatalan dan pemesanan ulang menjadi objek perintah
    interface OrderCommand {
        void execute();
    }

    static class CancelOrderCommand implements OrderCommand {
        private final Order order;

        CancelOrderCommand(Order order) {
            this.order = order;
        }

        public void execute() {
            order.cancel();
        }
    }

    static class ReorderCommand implements OrderCommand {
        private final OrderProcessingTemplate processor;
        private final CheckoutRequest previousRequest;
        private Order newOrder;

        ReorderCommand(OrderProcessingTemplate processor, CheckoutRequest previousRequest) {
            this.processor = processor;
            this.previousRequest = previousRequest;
        }

        public void execute() {
            newOrder = processor.process(previousRequest);
        }

        Order getNewOrder() {
            return newOrder;
        }
    }

    static class CommandInvoker {
        private final List<OrderCommand> history = new ArrayList<>();

        void execute(OrderCommand command) {
            command.execute();
            history.add(command);
        }

        int getExecutedCommandCount() {
            return history.size();
        }
    }

    // MAIN: demonstrasi alur sistem dari keranjang sampai pesanan selesai
    public static void main(String[] args) {
        Customer customer = new Customer("Alya", 100000);
        Restaurant restaurant = new Restaurant("Warung Bu Sari", true);

        MenuItem ayam = new MenuItem("Ayam Geprek", 25000, true);
        MenuItem esTeh = new MenuItem("Es Teh", 5000, true);

        // Pelanggan menambahkan menu ke keranjang
        Cart cart = new Cart();
        cart.add(ayam, 2);
        cart.add(esTeh, 2);
        System.out.println("Isi keranjang sebelum aplikasi ditutup: "
                + cart.getItems().size() + " jenis menu, subtotal " + money(cart.getSubtotal()));

        // Memento menyimpan kondisi keranjang, Cart baru memulihkannya
        CartHistory cartHistory = new CartHistory();
        cartHistory.backup(cart);
        System.out.println("Snapshot keranjang berhasil disimpan.");

        Cart restoredCart = new Cart(); // simulasi aplikasi dibuka kembali
        cartHistory.restore(restoredCart);
        System.out.println("Keranjang setelah dipulihkan: "
                + restoredCart.getItems().size() + " jenis menu, subtotal "
                + money(restoredCart.getSubtotal()));

        // Observer didaftarkan sebagai penerima pembaruan status
        List<OrderObserver> observers = Arrays.<OrderObserver>asList(
                new CustomerNotifier(),
                new RestaurantNotifier(),
                new CourierNotifier());

        // Strategi ongkir, diskon, dan pembayaran dipilih untuk checkout
        CheckoutRequest deliveryRequest = new CheckoutRequest(
                customer,
                restaurant,
                restoredCart.getItems(),
                ServiceType.DELIVERY,
                4.0,
                "HEMAT10",
                new PerKmShippingFee(3000),
                new PercentageDiscountStrategy(0.10),
                new WalletPaymentStrategy(),
                observers);

        OrderProcessingTemplate deliveryProcessor = new DeliveryOrderProcessor(createValidationChain());
        Order order = deliveryProcessor.process(deliveryRequest);

        // Kurir menyelesaikan pengantaran setelah pesanan berstatus DALAM_PENGANTARAN
        order.complete();
        System.out.println("Pesanan utama " + order.id + " berakhir dengan status: " + order.getStatus());
        System.out.println("Sisa saldo pelanggan: " + money(customer.getBalance()));

        // Command membungkus aksi pembatalan pesanan yang masih dikonfirmasi
        Order pendingOrder = new Order(customer, restaurant, restoredCart.getItems(),
                order.total, ServiceType.DELIVERY);
        pendingOrder.addObserver(new CustomerNotifier());
        pendingOrder.confirm();

        CommandInvoker commandInvoker = new CommandInvoker();
        commandInvoker.execute(new CancelOrderCommand(pendingOrder));
        System.out.println("Status pesanan yang dibatalkan: " + pendingOrder.getStatus());

        // Command juga membungkus pemesanan ulang berdasarkan detail checkout
        // sebelumnya
        CheckoutRequest reorderRequest = new CheckoutRequest(
                customer,
                restaurant,
                restoredCart.getItems(),
                ServiceType.DELIVERY,
                4.0,
                "HEMAT10",
                new PerKmShippingFee(3000),
                new PercentageDiscountStrategy(0.10),
                new CashOnDeliveryStrategy(),
                observers);

        ReorderCommand reorderCommand = new ReorderCommand(
                deliveryProcessor, reorderRequest);
        commandInvoker.execute(reorderCommand);
        Order repeatedOrder = reorderCommand.getNewOrder();
        System.out.println("Pesanan ulang dibuat: " + repeatedOrder.id
                + ", status: " + repeatedOrder.getStatus());
        repeatedOrder.complete();

        System.out.println("Jumlah command yang dieksekusi: "
                + commandInvoker.getExecutedCommandCount());
        System.out.println("Demonstrasi JemberEats selesai");
    }
}
