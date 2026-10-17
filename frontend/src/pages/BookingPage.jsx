import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { movies as moviesApi, seats as seatsApi, bookings as bookingsApi } from '../api';
import { useAuth } from '../context/AuthContext';
import { ArrowLeft, Monitor, Check, X, Lock, Armchair, CreditCard, Clock, AlertCircle } from 'lucide-react';
import './BookingPage.css';

const STATUS_LABELS = {
  AVAILABLE: 'Available',
  LOCKED: 'Locked',
  BOOKED: 'Booked',
};

export default function BookingPage() {
  const { scheduleId } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [schedule, setSchedule] = useState(null);
  const [seatsList, setSeatsList] = useState([]);
  const [selected, setSelected] = useState(new Set());
  const [loading, setLoading] = useState(true);
  const [booking, setBooking] = useState(null);
  const [bookingLoading, setBookingLoading] = useState(false);
  const [paymentLoading, setPaymentLoading] = useState(false);
  const [error, setError] = useState(null);
  const [pollCount, setPollCount] = useState(0);

  useEffect(() => {
    loadData();
  }, [scheduleId]);

  // Poll booking status when in PAYMENT_PENDING
  useEffect(() => {
    if (!booking || booking.status !== 'PAYMENT_PENDING') return;
    const timer = setTimeout(async () => {
      try {
        const updated = await bookingsApi.get(booking.id);
        setBooking(updated);
        if (updated.status === 'CONFIRMED') {
          // Refresh seats
          const freshSeats = await seatsApi.forSchedule(scheduleId);
          setSeatsList(freshSeats || []);
        }
      } catch {}
      setPollCount((c) => c + 1);
    }, 2000);
    return () => clearTimeout(timer);
  }, [booking, pollCount, scheduleId]);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [scheduleData, seatsData] = await Promise.all([
        moviesApi.schedule(scheduleId),
        seatsApi.forSchedule(scheduleId),
      ]);
      setSchedule(scheduleData);
      setSeatsList(seatsData || []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const toggleSeat = useCallback((seatNumber) => {
    if (booking) return; // Don't allow changes after booking
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(seatNumber)) next.delete(seatNumber);
      else next.add(seatNumber);
      return next;
    });
  }, [booking]);

  const totalPrice = Array.from(selected).reduce((sum, sn) => {
    const seat = seatsList.find((s) => s.seatNumber === sn);
    return sum + (seat ? parseFloat(seat.price) : 0);
  }, 0);

  const handleBook = async () => {
    setBookingLoading(true);
    setError(null);
    try {
      const result = await bookingsApi.create({
        userId: user.id,
        scheduleId,
        seatNumbers: Array.from(selected),
      });
      setBooking(result);
      // Refresh seats to show LOCKED status
      const freshSeats = await seatsApi.forSchedule(scheduleId);
      setSeatsList(freshSeats || []);
    } catch (err) {
      setError(err.message);
    } finally {
      setBookingLoading(false);
    }
  };

  const handlePay = async () => {
    setPaymentLoading(true);
    setError(null);
    try {
      const result = await bookingsApi.pay(booking.id);
      setBooking(result);
      setPollCount(0);
    } catch (err) {
      setError(err.message);
    } finally {
      setPaymentLoading(false);
    }
  };

  // Organize seats into rows
  const seatsByRow = seatsList.reduce((acc, seat) => {
    // Seat numbers are like "A1", "A2", "B1" etc.
    const row = seat.seatNumber.replace(/[0-9]/g, '') || seat.seatNumber;
    if (!acc[row]) acc[row] = [];
    acc[row].push(seat);
    return acc;
  }, {});

  // Sort rows alphabetically, seats numerically within each row
  const sortedRows = Object.keys(seatsByRow).sort();
  sortedRows.forEach((row) => {
    seatsByRow[row].sort((a, b) => {
      const numA = parseInt(a.seatNumber.replace(/[^0-9]/g, '')) || 0;
      const numB = parseInt(b.seatNumber.replace(/[^0-9]/g, '')) || 0;
      return numA - numB;
    });
  });

  const formatTime = (t) => {
    if (!t) return '';
    const [h, m] = t.split(':');
    const hour = parseInt(h);
    return `${hour % 12 || 12}:${m} ${hour >= 12 ? 'PM' : 'AM'}`;
  };

  if (loading) {
    return (
      <div className="movies-loading">
        <div className="spinner spinner-lg" />
      </div>
    );
  }

  return (
    <div className="booking-page container">
      <Link to={schedule ? `/movies/${schedule.movieId}` : '/movies'} className="back-link">
        <ArrowLeft size={18} />
        Back to Showtimes
      </Link>

      {schedule && (
        <motion.div
          className="booking-header"
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
        >
          <h1>{schedule.movieTitle}</h1>
          <div className="booking-meta">
            <span>{schedule.theaterName}</span>
            <span className="meta-dot">•</span>
            <span>{schedule.screenName}</span>
            <span className="meta-dot">•</span>
            <span>{new Date(schedule.showDate + 'T00:00:00').toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })}</span>
            <span className="meta-dot">•</span>
            <span className="booking-time">{formatTime(schedule.showTime)}</span>
          </div>
        </motion.div>
      )}

      {error && (
        <motion.div
          className="auth-error"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          style={{ marginBottom: 24 }}
        >
          <AlertCircle size={16} style={{ marginRight: 8, verticalAlign: 'middle' }} />
          {error}
        </motion.div>
      )}

      <div className="booking-layout">
        {/* Seat Map */}
        <div className="seat-map-container">
          <div className="screen-element screen-glow">
            <Monitor size={20} />
            <span>SCREEN</span>
          </div>

          <div className="seat-map">
            {sortedRows.map((row, rowIdx) => (
              <motion.div
                key={row}
                className="seat-row"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: rowIdx * 0.04, duration: 0.3 }}
              >
                <span className="seat-row-label">{row}</span>
                <div className="seat-row-seats">
                  {seatsByRow[row].map((seat) => {
                    const isSelected = selected.has(seat.seatNumber);
                    const isAvailable = seat.status === 'AVAILABLE';
                    const isBooked = seat.status === 'BOOKED';
                    const isLocked = seat.status === 'LOCKED';

                    return (
                      <motion.button
                        key={seat.id}
                        className={`seat ${isSelected ? 'seat-selected' : ''} ${isBooked ? 'seat-booked' : ''} ${isLocked ? 'seat-locked' : ''} ${isAvailable && !isSelected ? 'seat-available' : ''} seat-${(seat.category || 'STANDARD').toLowerCase()}`}
                        onClick={() => isAvailable && toggleSeat(seat.seatNumber)}
                        disabled={!isAvailable || !!booking}
                        whileHover={isAvailable && !booking ? { scale: 1.15 } : {}}
                        whileTap={isAvailable && !booking ? { scale: 0.95 } : {}}
                        title={`${seat.seatNumber} — ${STATUS_LABELS[seat.status]} — ₹${seat.price}`}
                        id={`seat-${seat.seatNumber}`}
                      >
                        {isSelected && <Check size={12} strokeWidth={3} />}
                        {isBooked && <X size={10} strokeWidth={3} />}
                        {isLocked && <Lock size={10} strokeWidth={3} />}
                      </motion.button>
                    );
                  })}
                </div>
                <span className="seat-row-label">{row}</span>
              </motion.div>
            ))}
          </div>

          {/* Legend */}
          <div className="seat-legend">
            <div className="legend-item">
              <span className="legend-dot legend-available" />
              Available
            </div>
            <div className="legend-item">
              <span className="legend-dot legend-selected" />
              Selected
            </div>
            <div className="legend-item">
              <span className="legend-dot legend-locked" />
              Locked
            </div>
            <div className="legend-item">
              <span className="legend-dot legend-booked" />
              Booked
            </div>
          </div>

          {/* Category legend if mixed */}
          {seatsList.some(s => s.category && s.category !== 'STANDARD') && (
            <div className="seat-categories">
              {['PREMIUM', 'VIP', 'STANDARD'].filter(c => seatsList.some(s => (s.category || 'STANDARD') === c)).map(c => (
                <div key={c} className="category-item">
                  <span className={`category-dot category-${c.toLowerCase()}`} />
                  {c.charAt(0) + c.slice(1).toLowerCase()}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Booking Summary Sidebar */}
        <AnimatePresence>
          <motion.div
            className="booking-sidebar glass"
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.4, delay: 0.2 }}
          >
            <h3 className="sidebar-title">
              <Armchair size={18} />
              Booking Summary
            </h3>

            {booking ? (
              <BookingStatus
                booking={booking}
                onPay={handlePay}
                paymentLoading={paymentLoading}
              />
            ) : (
              <>
                {selected.size > 0 ? (
                  <div className="selected-seats-list">
                    {Array.from(selected).sort().map((sn) => {
                      const seat = seatsList.find((s) => s.seatNumber === sn);
                      return (
                        <motion.div
                          key={sn}
                          className="selected-seat-chip"
                          initial={{ opacity: 0, scale: 0.8 }}
                          animate={{ opacity: 1, scale: 1 }}
                          exit={{ opacity: 0, scale: 0.8 }}
                          layout
                        >
                          <span className="chip-seat">{sn}</span>
                          <span className="chip-price">₹{seat?.price || '—'}</span>
                          <button
                            className="chip-remove"
                            onClick={() => toggleSeat(sn)}
                            aria-label={`Remove seat ${sn}`}
                          >
                            <X size={12} />
                          </button>
                        </motion.div>
                      );
                    })}
                  </div>
                ) : (
                  <p className="sidebar-hint">Select seats from the map to get started</p>
                )}

                <div className="sidebar-total">
                  <span>Total</span>
                  <span className="total-amount">
                    ₹{totalPrice.toFixed(2)}
                  </span>
                </div>

                <button
                  className="btn btn-primary btn-lg"
                  style={{ width: '100%' }}
                  disabled={selected.size === 0 || bookingLoading}
                  onClick={handleBook}
                  id="confirm-booking-btn"
                >
                  {bookingLoading ? (
                    <span className="spinner" />
                  ) : (
                    <>
                      <CreditCard size={18} />
                      Book {selected.size} Seat{selected.size !== 1 ? 's' : ''}
                    </>
                  )}
                </button>
              </>
            )}
          </motion.div>
        </AnimatePresence>
      </div>
    </div>
  );
}

/* Sub-component for booking status after creation */
function BookingStatus({ booking, onPay, paymentLoading }) {
  const navigate = useNavigate();

  const statusConfig = {
    SEATS_LOCKED: { label: 'Seats Locked', badge: 'badge-warning', icon: Lock },
    PENDING: { label: 'Pending', badge: 'badge-warning', icon: Clock },
    PAYMENT_PENDING: { label: 'Payment Processing', badge: 'badge-info', icon: CreditCard },
    CONFIRMED: { label: 'Confirmed!', badge: 'badge-success', icon: Check },
    FAILED: { label: 'Failed', badge: 'badge-error', icon: AlertCircle },
    EXPIRED: { label: 'Expired', badge: 'badge-error', icon: Clock },
    CANCELLED: { label: 'Cancelled', badge: 'badge-error', icon: X },
  };

  const config = statusConfig[booking.status] || statusConfig.PENDING;
  const StatusIcon = config.icon;

  return (
    <motion.div
      className="booking-status"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
    >
      <div className={`status-badge-lg ${config.badge}`}>
        <StatusIcon size={18} />
        {config.label}
      </div>

      <div className="status-details">
        <div className="status-row">
          <span>Booking ID</span>
          <span className="status-value mono">{booking.id?.substring(0, 8)}...</span>
        </div>
        <div className="status-row">
          <span>Seats</span>
          <span className="status-value">{booking.seatNumbers?.join(', ')}</span>
        </div>
        <div className="status-row">
          <span>Amount</span>
          <span className="status-value">₹{booking.totalAmount}</span>
        </div>
      </div>

      {(booking.status === 'SEATS_LOCKED' || booking.status === 'PENDING') && (
        <>
          {booking.lockExpiresAt && (
            <CountdownTimer expiresAt={booking.lockExpiresAt} />
          )}
          <button
            className="btn btn-primary btn-lg"
            style={{ width: '100%', marginTop: 12 }}
            onClick={onPay}
            disabled={paymentLoading}
            id="pay-now-btn"
          >
            {paymentLoading ? <span className="spinner" /> : <><CreditCard size={18} /> Pay Now</>}
          </button>
        </>
      )}

      {booking.status === 'PAYMENT_PENDING' && (
        <div className="processing-indicator">
          <div className="spinner" />
          <span>Processing payment...</span>
        </div>
      )}

      {booking.status === 'CONFIRMED' && (
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ type: 'spring', stiffness: 300 }}
        >
          <div className="confirmed-message">
            🎉 Your tickets are booked!
          </div>
          <button
            className="btn btn-secondary"
            style={{ width: '100%', marginTop: 12 }}
            onClick={() => navigate('/my-tickets')}
          >
            View My Tickets
          </button>
        </motion.div>
      )}

      {booking.status === 'FAILED' && booking.failureReason && (
        <div className="auth-error" style={{ marginTop: 12, fontSize: '0.82rem' }}>
          {booking.failureReason}
        </div>
      )}
    </motion.div>
  );
}

function CountdownTimer({ expiresAt }) {
  const [remaining, setRemaining] = useState('');

  useEffect(() => {
    const update = () => {
      const now = Date.now();
      const exp = new Date(expiresAt).getTime();
      const diff = Math.max(0, exp - now);
      const mins = Math.floor(diff / 60000);
      const secs = Math.floor((diff % 60000) / 1000);
      setRemaining(`${mins}:${secs.toString().padStart(2, '0')}`);
    };
    update();
    const interval = setInterval(update, 1000);
    return () => clearInterval(interval);
  }, [expiresAt]);

  return (
    <div className="countdown">
      <Clock size={14} />
      <span>Lock expires in <strong>{remaining}</strong></span>
    </div>
  );
}
