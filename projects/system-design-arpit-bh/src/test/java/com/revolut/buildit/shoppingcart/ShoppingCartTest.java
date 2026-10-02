package com.revolut.buildit.shoppingcart;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class ShoppingCartTest {
    @Test
    public void shouldRegisterNewUser(){
        ShoppingCartSolution solution = new ShoppingCartSolution();
        User createdUser = solution.registerUser("aman", "user1");
        User user = solution.getUser("user1");

        assertEquals(user,  createdUser);
    }

    @Test
    public void shouldReturnNullWhenUserNotPresent(){
        ShoppingCartSolution solution = new ShoppingCartSolution();
        User user = solution.getUser("user1");
        assertNull(user);
    }

    @Test
    public void shouldNotRegisterNewUserWhenAlreadyPresent(){
        ShoppingCartSolution solution = new ShoppingCartSolution();
        User createdUser = solution.registerUser("aman", "user1");
        User user = solution.getUser("user1");

        assertEquals(user,  createdUser);
        assertThrows(RuntimeException.class,() -> solution.registerUser("aman", "user1"));
    }

    @Test
    public void shouldAddcartToUser(){
        ShoppingCartSolution solution = new ShoppingCartSolution();
        User createdUser = solution.registerUser("aman", "user1");
        User user = solution.getUser("user1");

        assertEquals(user,  createdUser);
        Cart c = user.addNewCart();
        assertNotNull(c);
        assertEquals(c.items.size(), 0);
    }

    @Test
    public void shouldAddItemsTocart(){
        ShoppingCartSolution solution = new ShoppingCartSolution();
        User createdUser = solution.registerUser("aman", "user1");
        User user = solution.getUser("user1");

        assertEquals(user,  createdUser);
        Cart c = user.addNewCart();
        assertNotNull(c);
        assertEquals(c.items.size(), 0);

        CartItem cartItem = new CartItem();
        cartItem.id = "item1";
        cartItem.quantity = 2;
        cartItem.price = 10;

        CartItem cartItem1 = solution.addItemToCart(c.id, "user1", cartItem);
        assertEquals(cartItem.quantity, cartItem1.quantity);

        CartItem cartItem2 = solution.addItemToCart(c.id, "user1", cartItem);
        assertEquals(4, cartItem2.quantity);
    }

    @Test
    public void shouldReturnNullIfCartIsEmpty(){
        ShoppingCartSolution solution = new ShoppingCartSolution();
        User createdUser = solution.registerUser("aman", "user1");
        User user = solution.getUser("user1");

        assertEquals(user,  createdUser);

        CartItem cartItem = new CartItem();
        cartItem.id = "item1";
        cartItem.quantity = 2;

        CartItem cartItem1 = solution.addItemToCart("randomId", "user1", cartItem);
        assertNull(cartItem1);
    }

    @Test
    public void shouldCalculateTotlaPrice(){
        ShoppingCartSolution solution = new ShoppingCartSolution();
        User createdUser = solution.registerUser("aman", "user1");
        User user = solution.getUser("user1");

        assertEquals(user,  createdUser);
        Cart c = solution.registerCartToUser(user.userId);
        assertNotNull(c);
        assertEquals(c.items.size(), 0);

        CartItem cartItem = new CartItem();
        cartItem.id = "item1";
        cartItem.quantity = 2;
        cartItem.price = 10;

        CartItem cartItem1 = solution.addItemToCart(c.id, "user1", cartItem);
        assertEquals(cartItem.quantity, cartItem1.quantity);

        CartItem cartItem2 = solution.addItemToCart(c.id, "user1", cartItem);
        assertEquals(4, cartItem2.quantity);

        long totalPrice = solution.calculateTotalPrice("user1", c.id);
        assertEquals(40, totalPrice);
    }

    @Test
    public void shouldHandleConcurrentModificationToCart() throws Exception{
        ShoppingCartSolution solution = new ShoppingCartSolution();
        User createdUser = solution.registerUser("aman", "user1");
        User user = solution.getUser("user1");

        assertEquals(user,  createdUser);
        Cart c = solution.registerCartToUser(user.userId);
        assertNotNull(c);
        assertEquals(c.items.size(), 0);

        CartItem cartItem = new CartItem();
        cartItem.id = "item1";
        cartItem.quantity = 2;
        cartItem.price = 10;

        int threadCount = 10;
        CyclicBarrier  barrier = new CyclicBarrier(threadCount+1); // +1 fr the main thread
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        try{
            List<Future<CartItem>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                Future<CartItem> future = executorService.submit(() -> {
                    barrier.await(2, TimeUnit.SECONDS);
                    CartItem temp = new CartItem("item1", "name", 10, 2);
                    return solution.addItemToCart(c.id, "user1", temp);
                });
                futures.add(future);
            }
            barrier.await(2, TimeUnit.SECONDS);
            long total = 0;
            for(Future<CartItem> future : futures){
                assertNotNull(future.get());
            }

            long totalPrice = solution.calculateTotalPrice("user1", c.id);
            assertEquals(totalPrice, 200);
            assertEquals(user.carts.get(c.id).items.get("item1").quantity, 20);
        }finally {
            executorService.shutdown();
        }
    }
}
