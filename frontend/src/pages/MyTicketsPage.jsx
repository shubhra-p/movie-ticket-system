import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '../context/AuthContext';
import { bookings as bookingsApi } from '../api';
import { Ticket, Clock, Check, X, AlertCircle, CreditCard, RefreshCw } from 'lucide-react';
import './MyTicketsPage.css';

const STATUS_MAP = {
  PENDING: { label: 'Pending', badge: 'badge-warning', icon: Clock },
  SEATS_LOCKED: { label: 'Seats Locked', badge: 'badge-warning', icon: Clock },
  PAYMENT_PENDING: { label: 'Processing', badge: 'badge-info', icon: CreditCard },
  CONFIRMED: { label: 'Confirmed', badge: 'badge-success', icon: Check },
  FAILED: { label: 'Failed', badge: 'badge-error', icon: AlertCircle },
  EXPIRED: { label: 'Expired', badge: 'badge-error', icon: Clock },
  CANCELLED: { label: 'Cancelled', badge: 'badge-neutral', icon: X },
};

export default function MyTicketsPage() {
  const { user } = useAuth();
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadTickets();
  }, [user?.id]);

  const loadTickets = async () => {
    if (!user?.id) return;
    setLoading(true);
    setError(null);
    try {
      const data = await bookingsApi.forUser(user.id);
      setTickets(data || []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const container = {
    hidden: {},
    show: { transition: { staggerChildren: 0.05 } },
  };

  const item = {
    hidden: { opacity: 0, y: 15 },
    show: { opacity: 1, y: 0, transition: { duration: 0.35 } },
  };

  return (
    <div className="tickets-page container">
      <motion.div
        className="tickets-header"
        initial={{ opacity: 0, y: -15 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <div>
          <h1>My Tickets</h1>
          <p className="tickets-subtitle">Your booking history</p>
        </div>
        <button className="btn btn-ghost btn-sm" onClick={loadTickets} id="refresh-tickets-btn">
          <RefreshCw size={15} />
          Refresh
        </button>
      </motion.div>

      {error && <div className="auth-error" style={{ marginBottom: 24 }}>{error}</div>}

      {loading ? (
        <div className="movies-loading">
          <div className="spinner spinner-lg" />
        </div>
      ) : tickets.length === 0 ? (
        <motion.div
          className="tickets-empty"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
        >
          <Ticket size={48} strokeWidth={1.5} />
          <h3>No tickets yet</h3>
          <p>Your bookings will appear here after you book seats.</p>
        </motion.div>
      ) : (
        <motion.div
          className="tickets-list"
          variants={container}
          initial="hidden"
          animate="show"
        >
          {tickets
            .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
            .map((ticket) => {
              const config = STATUS_MAP[ticket.status] || STATUS_MAP.PENDING;
              const StatusIcon = config.icon;

              return (
                <motion.div
                  key={ticket.id}
                  className="ticket-card card"
                  variants={item}
                  id={`ticket-${ticket.id}`}
                >
                  <div className="ticket-left">
                    <div className={`ticket-status badge ${config.badge}`}>
                      <StatusIcon size={12} />
                      {config.label}
                    </div>
                    <div className="ticket-id">
                      #{ticket.id?.substring(0, 8)}
                    </div>
                  </div>

                  <div className="ticket-center">
                    <div className="ticket-seats">
                      <span className="ticket-label">Seats</span>
                      <span className="ticket-value">{ticket.seatNumbers?.join(', ') || '—'}</span>
                    </div>
                    <div className="ticket-date">
                      <span className="ticket-label">Booked</span>
                      <span className="ticket-value">
                        {ticket.createdAt
                          ? new Date(ticket.createdAt).toLocaleDateString('en-US', {
                              month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
                            })
                          : '—'}
                      </span>
                    </div>
                  </div>

                  <div className="ticket-right">
                    <span className="ticket-amount">₹{ticket.totalAmount || '0.00'}</span>
                  </div>

                  {/* Decorative ticket notch */}
                  <div className="ticket-notch ticket-notch-left" />
                  <div className="ticket-notch ticket-notch-right" />
                </motion.div>
              );
            })}
        </motion.div>
      )}
    </div>
  );
}
