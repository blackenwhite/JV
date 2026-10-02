package com.revolut.buildit.shoppingcart;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class ShoppingCartSolution {
    Map<String, User> users = new ConcurrentHashMap<>();

    public User registerUser(String name, String id){
        if(users.containsKey(id)){
            throw new RuntimeException("User already exists");
        }
        User user = new User(name, id);
        users.put(id, user);
        return user;
    }

    public User getUser(String id) {
        return users.get(id);
    }

    public Cart registerCartToUser(String userId) {
        User user = getUser(userId);
        if(user == null){
            System.out.println("user not found");
            return null;
        }
        return user.addNewCart();
    }

    public CartItem addItemToCart(String cartId, String userId, CartItem item) {
        User user = getUser(userId);
        if(user == null){
            System.out.println("user not found");
            return null;
        }
        return user.addItemToCart(cartId, item);
    }

    public long calculateTotalPrice(String userId, String cartId){
        User user = getUser(userId);
        if(user == null){
            System.out.println("user not found");
            return 0;
        }
        return user.calculateTotalPrice(cartId);
    }
}

class Cart{
    String id;
    Map<String, CartItem> items;

    public Cart(String id) {
        this.id = id;
        this.items = new HashMap<>();
    }
}

class CartItem {
    String id;
    String name;
    long price;
    int quantity;

    public CartItem(String id, String name, long price, int quantity) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public CartItem() {
    }
}

class CartItem2{
    String id;
    String name;
    AtomicReference<CartState> cartState = new AtomicReference<>(new CartState(0,0));

    public CartItem2(String id, String name) {
        this.id = id;
        this.name = name;
    }

    void addQuantity(int amount){
        int numRetries = 10;
        for(int i=0;i<10;i++) {
            CartState current =  cartState.get();
            CartState newState = new CartState(current.quantity+amount, current.version+1);
            if(cartState.compareAndSet(current, newState)) {
                return;
            }
        }
    }
}

class CartState{
    final int quantity;
    final long version;
    public CartState(int quantity, long version) {
        this.quantity = quantity;
        this.version = version;
    }
}

class User {
    String name;
    String userId;
    Map<String, Cart> carts;
    private final ReentrantLock lock = new ReentrantLock();

    public User(String name, String userId) {
        this.name = name;
        this.userId = userId;
        carts = new HashMap<>();
    }

    Cart addNewCart(){
        try{
            lock.lock();
            Cart newCart = new Cart(UUID.randomUUID().toString());
            carts.put(newCart.id, newCart);
            return newCart;
        }finally {
            lock.unlock();
        }
    }

    public CartItem addItemToCart(String cartId, CartItem item) {
        try{
            lock.lock();
            Cart cart = carts.get(cartId);
            if(cart == null){
                System.out.println("cart not found");
                return null;
            }
            if(cart.items.containsKey(item.id)){
                CartItem existingItem = cart.items.get(item.id);
                existingItem.quantity += item.quantity;
            } else {
                cart.items.put(item.id, item);
            }
            return cart.items.get(item.id);
        }finally {
            lock.unlock();
        }

    }

    public long calculateTotalPrice(String cartId){
        try{
            lock.lock();
            Cart cart = carts.get(cartId);
            if(cart == null){
                System.out.println("cart not found");
                return 0;
            }
            long ans = 0;
            for(CartItem item : cart.items.values()){
                ans += item.price*item.quantity;
            }
            return ans;
        }finally{
            lock.unlock();
        }

    }
}