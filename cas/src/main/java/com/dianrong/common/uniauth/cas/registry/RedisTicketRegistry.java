package com.dianrong.common.uniauth.cas.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.jasig.cas.authentication.principal.Service;
import org.jasig.cas.ticket.ServiceTicket;
import org.jasig.cas.ticket.Ticket;
import org.jasig.cas.ticket.TicketGrantingTicket;
import org.jasig.cas.ticket.registry.AbstractDistributedTicketRegistry;
import org.springframework.data.redis.core.RedisTemplate;

import com.dianrong.common.uniauth.common.cons.AppConstants;

public class RedisTicketRegistry extends AbstractDistributedTicketRegistry{
    @NotNull
    private final RedisTemplate<String,Object> redisTemplate;

    /**
     * TGT cache entry timeout in seconds.
     */
    @Min(0)
    private final int tgtTimeout;
    
    /**.
     * zk config map
     */
    @Resource(name="uniauthConfig")
	private Map<String, String> allZkNodeMap;

    /**
     * ST cache entry timeout in seconds.
     */
    @Min(0)
    private final int stTimeout;
    
    public RedisTicketRegistry(RedisTemplate<String,Object> redisTemplate,int tgtTimeout,int stTimeout){
        this.redisTemplate=redisTemplate;
        this.tgtTimeout=tgtTimeout;
        this.stTimeout=stTimeout;
    }
    @Override
    public void addTicket(Ticket ticket) {
        logger.debug("Adding ticket {}", ticket);
        try {
        	redisTemplate.opsForValue().set(ticket.getId(),ticket, getTimeout(ticket), TimeUnit.SECONDS);
        } catch (final Exception e) {
            logger.error("Failed adding {}", ticket, e);
            throw e;
        }
    }

    @Override
    public Ticket getTicket(String ticketId) {
         try {
                final Ticket t = (Ticket) this.redisTemplate.opsForValue().get(ticketId);
                if (t != null) {
                    return getProxiedTicketInstance(t);
                }
            } catch (final Exception e) {
                logger.error("Failed fetching {} ", ticketId, e);
                throw e;
            }
            return null;
    }

    @Override
    public boolean deleteTicket(String ticketId) {
         logger.debug("Deleting ticket {}", ticketId);
            try {
            	Ticket t = getTicket(ticketId);
            	if(t instanceof TicketGrantingTicket) {
            		deleteChildren((TicketGrantingTicket)t);
            	}
            	// if it is ServiceTicket and don't delete it 
            	if(t instanceof ServiceTicket && !isStReusedNotAllowed()) {
            		return true;
            	}
            	this.redisTemplate.delete(ticketId);
            	return true;
            } catch (final Exception e) {
                logger.error("Failed deleting {}", ticketId, e);
                throw e;
            }
    }
    
    /**
     * Delete TGT's service tickets.
     *
     * @param ticket the ticket
     */
    private void deleteChildren(final TicketGrantingTicket ticket) {
        // delete service tickets
        final Map<String, Service> services = ticket.getServices();
        if (services != null && !services.isEmpty()) {
            for (final Map.Entry<String, Service> entry : services.entrySet()) {
                if (this.deleteServiceTicket(entry.getKey())) {
                    logger.trace("Removed service ticket [{}]", entry.getKey());
                } else {
                    logger.trace("Unable to remove service ticket [{}]", entry.getKey());
                }
            }
        }
    }
    /**.
     * 指定删除service ticket
     * @param ticketId
     * @return
     */
    private boolean deleteServiceTicket(String ticketId) {
    	if(ticketId == null) {
    		return false;
    	}
    	  logger.debug("Deleting ticket {}", ticketId);
          try {
          		this.redisTemplate.delete(ticketId);
               return true;
          } catch (final Exception e) {
              logger.error("Failed deleting {}", ticketId, e);
              throw e;
          }
    }
    
    @Override
    public Collection<Ticket> getTickets() {
         //throw new UnsupportedOperationException("GetTickets not supported.");
    	return new ArrayList<Ticket>();
    }

    @Override
    protected void updateTicket(Ticket ticket) {
     logger.debug("Updating ticket {}", ticket);
        try {
              this.redisTemplate.delete(ticket.getId());
              redisTemplate.opsForValue().set(ticket.getId(),ticket, getTimeout(ticket), TimeUnit.SECONDS);
        } catch (final Exception e) {
            logger.error("Failed updating {}", ticket, e);
            throw e;
        }
    }

    @Override
    protected boolean needsCallback() {
        return true;
    }
   private int getTimeout(final Ticket t) {
        if (t instanceof TicketGrantingTicket) {
            return this.tgtTimeout;
        } else if (t instanceof ServiceTicket) {
            return this.stTimeout;
        }
        throw new IllegalArgumentException("Invalid ticket type");
    }
   
   /**.
    * 动态判断是否需要主动删除service ticket
    * @return true  or false
    */
   private boolean isStReusedNotAllowed(){
	   String val = allZkNodeMap.get(AppConstants.ZK_NODE_NAME_REUSE_ST_NOT_ALLOWED);
	   if(val == null || !"true".equals(val)) {
		   return false;
	   } 
	   return true;
   }
}