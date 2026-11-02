package com.example.learning.casestudy.service;


import com.example.learning.casestudy.adapter.group10.case11.LazyItems;

import com.example.learning.casestudy.adapter.group10.case11.Session;

import com.example.learning.casestudy.application.group10.case12.ExpiryService;

import com.example.learning.casestudy.application.group10.case6.Dispatcher;

import com.example.learning.casestudy.application.group10.case7.BrokenConsumer;

import com.example.learning.casestudy.application.group10.case7.InventoryConsumer;

import com.example.learning.casestudy.application.group10.case8.Saga;

import com.example.learning.casestudy.application.group10.case9.CancelTicket;

import com.example.learning.casestudy.domain.group10.case1.Customer;

import com.example.learning.casestudy.domain.group10.case10.Order10;

import com.example.learning.casestudy.domain.group10.case10.Status;

import com.example.learning.casestudy.domain.group10.case11.OrderEntity;

import com.example.learning.casestudy.domain.group10.case2.BadEntity;

import com.example.learning.casestudy.domain.group10.case2.Entity;

import com.example.learning.casestudy.domain.group10.case2.EntityKey;

import com.example.learning.casestudy.domain.group10.case3.Address;

import com.example.learning.casestudy.domain.group10.case4.Money;

import com.example.learning.casestudy.domain.group10.case5.Order5;

import com.example.learning.casestudy.domain.group10.case7.Event7;

import com.example.learning.casestudy.domain.group10.case9.Ticket;

import com.example.learning.casestudy.dto.group10.case11.OrderDto;

import com.example.learning.casestudy.model.group10.case1.BadCustomer;

import com.example.learning.casestudy.model.group10.case10.BrokenOrder;

import com.example.learning.casestudy.model.group10.case3.BadAddress;

import com.example.learning.casestudy.model.group10.case5.BadOrder;

import com.example.learning.casestudy.repository.group10.case6.DatabaseSimulation;

import com.example.learning.casestudy.repository.group10.case8.BillingStore;

import com.example.learning.casestudy.repository.group10.case8.BookingStore;

import com.example.learning.casestudy.utils.group10.case1.Case1Utils;

import com.example.learning.casestudy.utils.group10.case10.Case10Utils;

import com.example.learning.casestudy.utils.group10.case11.Case11Utils;

import com.example.learning.casestudy.utils.group10.case12.Case12Utils;

import com.example.learning.casestudy.utils.group10.case2.Case2Utils;

import com.example.learning.casestudy.utils.group10.case3.Case3Utils;

import com.example.learning.casestudy.utils.group10.case4.Case4Utils;

import com.example.learning.casestudy.utils.group10.case5.Case5Utils;

import com.example.learning.casestudy.utils.group10.case6.Case6Utils;

import com.example.learning.casestudy.utils.group10.case7.Case7Utils;

import com.example.learning.casestudy.utils.group10.case8.Case8Utils;

import com.example.learning.casestudy.utils.group10.case9.Case9Utils;

import java.math.BigDecimal;

import java.time.Clock;

import java.time.Duration;

import java.time.Instant;

import java.time.LocalDate;

import java.time.ZoneId;

import java.time.ZoneOffset;

import java.util.ArrayList;

import java.util.HashSet;

import java.util.List;

import java.util.Set;

import java.util.UUID;

import org.springframework.stereotype.Service;


@Service("caseStudyGroup10Service")
public class Group10Service {

    public void executeCase1() {

        Set<BadCustomer> wrong=new HashSet<>();

        wrong.add(new BadCustomer("An"));
        wrong.add(new BadCustomer("An"));

        Case1Utils.check(wrong.size()==1);
        // Hai bản ghi khác bị gộp bởi tên.
        Customer a=new Customer("C1","An"),b=new Customer("C2","An");

        Set<Customer> good=new HashSet<>(List.of(a,b));

        Case1Utils.check(good.size()==2);

        a.rename("Bình");

        Case1Utils.check(good.contains(new Customer("C1","Bình")));

        Case1Utils.check(!a.equals(b));

        System.out.println("PASS: stable Customer identity");

    }


    public void executeCase2() {

        BadEntity wrong=new BadEntity();

        Set<BadEntity> broken=new HashSet<>();

        broken.add(wrong);

        int before=wrong.hashCode();

        wrong.databaseId=1L;

        Case2Utils.check(before!=wrong.hashCode());

        // HashSet lookup sau mutate có thể không còn tìm được entry;
        // không phụ thuộc bucket collision để biến đây thành test bất ổn.
        Entity stable=new Entity(new EntityKey(UUID.randomUUID()));

        Set<Entity> good=new HashSet<>();

        good.add(stable);

        int hash=stable.hashCode();

        stable.databaseId=1L;

        Case2Utils.check(stable.hashCode()==hash && good.contains(stable));

        System.out.println("PASS: hash key independent of generated DB id");

    }


    public void executeCase3() {

        BadAddress a=new BadAddress("Số 1","Hà Nội");

        BadAddress b=new BadAddress("Số 1","Hà Nội");

        Case3Utils.check(a!=b && !a.equals(b));

        Address x=new Address(" Số   1 "," HÀ NỘI");

        Address y=new Address("số 1","hà nội");

        Case3Utils.check(x.equals(y));

        Case3Utils.check(x.hashCode()==y.hashCode());

        Case3Utils.check(new HashSet<>(List.of(x,y)).size()==1);

        try{
            new Address(" ","HN");
            throw new AssertionError();
        }

        catch(IllegalArgumentException expected){
        }

        System.out.println("PASS: normalized value equality");

    }


    public void executeCase4() {

        double wrong=0.1+0.2;

        Case4Utils.check(wrong!=0.3);
        // Sai số IEEE-754.
        Money a=new Money("USD",new BigDecimal("0.10"));

        Money b=new Money("USD",new BigDecimal("0.20"));

        Money total=a.plus(b);

        Case4Utils.check(total.amount().equals(new BigDecimal("0.30")));

        Money sum=total.plus(new Money("USD",new BigDecimal("0.70")));

        Case4Utils.check(sum.amount().equals(new BigDecimal("1.00")));

        try{
            a.plus(new Money("EUR",new BigDecimal("0.20")));

            throw new AssertionError("cross-currency accepted");

        }
        catch(IllegalArgumentException expected){
        }

        System.out.println("PASS: precise Money addition");

    }


    public void executeCase5() {

        BadOrder broken=new BadOrder();

        broken.item().qty=3;

        Case5Utils.check(broken.storedTotal!=broken.item().qty*100);

        Order5 valid=new Order5();

        valid.add("A",2,100);

        Case5Utils.check(valid.total()==200);

        try{
            valid.add("B",-1,50);
            throw new AssertionError();
        }

        catch(IllegalArgumentException expected){
        }

        try{
            valid.items().clear();
            throw new AssertionError();
        }

        catch(UnsupportedOperationException expected){
        }

        Case5Utils.check(valid.total()==200 && valid.items().size()==1);

        System.out.println("PASS: aggregate derived total");

    }


    public void executeCase6() {

        DatabaseSimulation db=new DatabaseSimulation();

        List<String> mail=new ArrayList<>();

        try{
            db.commit("O1",false);
            throw new AssertionError();
        }

        catch(IllegalArgumentException expected){
        }

        Case6Utils.check(db.orders.isEmpty()&&db.outbox.isEmpty()&&mail.isEmpty());

        db.commit("O1",true);

        Case6Utils.check(mail.isEmpty());
        // Chưa phát trước commit.
        Dispatcher sender=new Dispatcher();

        sender.sendPending(db,mail);

        Case6Utils.check(mail.equals(List.of("O1")));

        System.out.println("PASS: simulated commit precedes notification");

    }


    public void executeCase7() {

        Event7 same=new Event7("E1",2);

        BrokenConsumer wrong=new BrokenConsumer();

        wrong.handle(same);
        wrong.handle(same);

        Case7Utils.check(wrong.stock==6&&wrong.emails==2);

        InventoryConsumer good=new InventoryConsumer();

        good.handle(same);
        good.handle(same);

        Case7Utils.check(good.stock==8&&good.emails==1&&good.processed.size()==1);

        good.handle(new Event7("E2",1));

        Case7Utils.check(good.stock==7&&good.emails==2);

        System.out.println("PASS: duplicate event ignored");

    }


    public void executeCase8() {

        BookingStore a=new BookingStore();

        BillingStore b=new BillingStore();

        Saga saga=new Saga(a,b);

        Case8Utils.check(!saga.execute(false));

        Case8Utils.check(!a.reserved&&!b.charged);

        Case8Utils.check(saga.execute(true));

        Case8Utils.check(a.reserved&&b.charged);

        System.out.println("PASS: compensating flow illustrated");

    }


    public void executeCase9() {

        Instant dep=Instant.parse("2026-11-30T12:00:00Z");

        Ticket ticket=new Ticket("T1",dep,false);

        CancelTicket useCase=new CancelTicket();

        java.util.function.Function<Instant,Ticket> web=t->useCase.cancel(ticket,t);

        java.util.function.Function<Instant,Ticket> mobile=t->useCase.cancel(ticket,t);

        java.util.function.Function<Instant,Ticket> batch=t->useCase.cancel(ticket,t);

        Instant valid=dep.minus(Duration.ofHours(25));

        Instant tooLate=dep.minus(Duration.ofHours(23));

        Case9Utils.check(web.apply(valid).cancelled()&&mobile.apply(valid).cancelled()
        &&batch.apply(valid).cancelled());

        Case9Utils.reject(()->web.apply(tooLate));

        Case9Utils.reject(()->mobile.apply(tooLate));

        Case9Utils.reject(()->batch.apply(tooLate));

        System.out.println("PASS: all channels share cancellation guard");

    }


    public void executeCase10() {

        BrokenOrder bad=new BrokenOrder();

        bad.status=Status.REFUNDED;

        Case10Utils.check(bad.status==Status.REFUNDED);

        Order10 valid=new Order10();

        Case10Utils.rejects(valid::refund);

        Case10Utils.check(valid.status()==Status.NEW);

        valid.recordPayment();
        valid.refund();

        Case10Utils.check(valid.status()==Status.REFUNDED);

        Case10Utils.rejects(valid::recordPayment);

        Case10Utils.rejects(valid::refund);

        System.out.println("PASS: guarded order transitions");

    }


    public void executeCase11() {

        Session oldSession=new Session();

        OrderEntity bad=new OrderEntity("O1",new LazyItems(oldSession));

        oldSession.close();

        try{
            bad.items.get();
            throw new AssertionError();
        }

        catch(IllegalStateException expected){
        }

        Session goodSession=new Session();

        OrderEntity entity=new OrderEntity("O1",new LazyItems(goodSession));

        OrderDto dto=Case11Utils.mapWithinBoundary(entity);

        goodSession.close();

        Case11Utils.check(dto.id().equals("O1")&&dto.items().equals(List.of("A","B")));

        System.out.println("PASS: API DTO detached from lazy state");

    }


    public void executeCase12() {

        Instant now=Instant.parse("2026-11-01T00:00:00Z");

        Clock fixed=Clock.fixed(now,ZoneOffset.UTC);

        ExpiryService service=new ExpiryService(fixed);

        Case12Utils.check(!service.expired(now.plusSeconds(1)));

        Case12Utils.check(service.expired(now));
        // Đúng thời điểm hết hạn.
        Case12Utils.check(service.expired(now.minusSeconds(1)));

        Case12Utils.check(service.today().equals(LocalDate.of(2026,11,1)));

        Clock hcm=Clock.fixed(now,ZoneId.of("Asia/Ho_Chi_Minh"));

        Case12Utils.check(new ExpiryService(hcm).today().equals(LocalDate.of(2026,11,1)));

        try{
            service.expired(null);
            throw new AssertionError();
        }

        catch(IllegalArgumentException expected){
        }

        System.out.println("PASS: fixed Clock yields deterministic boundary");

    }

}
