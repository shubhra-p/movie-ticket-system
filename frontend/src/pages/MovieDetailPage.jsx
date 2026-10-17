import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { movies as moviesApi } from '../api';
import { Clock, Calendar, MapPin, ArrowLeft, ChevronRight } from 'lucide-react';
import './MovieDetailPage.css';

export default function MovieDetailPage() {
  const { movieId } = useParams();
  const [movie, setMovie] = useState(null);
  const [schedules, setSchedules] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadData();
  }, [movieId]);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [movieData, schedulesData] = await Promise.all([
        moviesApi.get(movieId),
        moviesApi.schedules(movieId),
      ]);
      setMovie(movieData);
      setSchedules(schedulesData || []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  // Group schedules by date
  const schedulesByDate = schedules.reduce((acc, s) => {
    const date = s.showDate;
    if (!acc[date]) acc[date] = [];
    acc[date].push(s);
    return acc;
  }, {});

  const formatDate = (dateStr) => {
    const d = new Date(dateStr + 'T00:00:00');
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 1);

    if (d.getTime() === today.getTime()) return 'Today';
    if (d.getTime() === tomorrow.getTime()) return 'Tomorrow';
    return d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
  };

  const formatTime = (timeStr) => {
    if (!timeStr) return '';
    const [h, m] = timeStr.split(':');
    const hour = parseInt(h);
    const ampm = hour >= 12 ? 'PM' : 'AM';
    const h12 = hour % 12 || 12;
    return `${h12}:${m} ${ampm}`;
  };

  if (loading) {
    return (
      <div className="movies-loading">
        <div className="spinner spinner-lg" />
      </div>
    );
  }

  if (error || !movie) {
    return (
      <div className="container" style={{ paddingTop: 60, textAlign: 'center' }}>
        <p className="auth-error">{error || 'Movie not found'}</p>
        <Link to="/movies" className="btn btn-secondary" style={{ marginTop: 16 }}>Back to Movies</Link>
      </div>
    );
  }

  return (
    <div className="movie-detail container">
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.4 }}
      >
        <Link to="/movies" className="back-link">
          <ArrowLeft size={18} />
          Back to Movies
        </Link>

        <div className="movie-detail-header">
          <div className="movie-detail-info">
            <h1>{movie.title}</h1>
            <div className="movie-meta" style={{ marginTop: 12, marginBottom: 16 }}>
              <span className="badge badge-neutral">{movie.genre}</span>
              <span className="movie-meta-item"><Clock size={14} /> {movie.durationMinutes} min</span>
              {movie.language && <span className="movie-meta-item">{movie.language}</span>}
              {movie.releaseDate && (
                <span className="movie-meta-item">
                  <Calendar size={14} /> {new Date(movie.releaseDate).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })}
                </span>
              )}
            </div>
            {movie.description && <p className="movie-detail-desc">{movie.description}</p>}
          </div>
        </div>

        <div className="schedules-section">
          <h2>Showtimes</h2>
          {schedules.length === 0 ? (
            <p className="schedules-empty">No showtimes available for this movie.</p>
          ) : (
            <div className="schedules-list">
              {Object.entries(schedulesByDate)
                .sort(([a], [b]) => a.localeCompare(b))
                .map(([date, shows]) => (
                  <motion.div
                    key={date}
                    className="schedule-date-group"
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.3 }}
                  >
                    <h3 className="schedule-date-label">
                      <Calendar size={16} />
                      {formatDate(date)}
                    </h3>
                    <div className="schedule-slots">
                      {shows
                        .sort((a, b) => (a.showTime || '').localeCompare(b.showTime || ''))
                        .map((schedule) => (
                          <Link
                            key={schedule.id}
                            to={`/book/${schedule.id}`}
                            className="schedule-slot card"
                            id={`schedule-${schedule.id}`}
                          >
                            <div className="schedule-slot-time">{formatTime(schedule.showTime)}</div>
                            <div className="schedule-slot-info">
                              <span className="schedule-slot-theater">
                                <MapPin size={13} />
                                {schedule.theaterName}
                              </span>
                              <span className="schedule-slot-screen">{schedule.screenName}</span>
                            </div>
                            <div className="schedule-slot-seats">
                              {schedule.totalSeats} seats
                            </div>
                            <ChevronRight size={16} className="schedule-slot-arrow" />
                          </Link>
                        ))}
                    </div>
                  </motion.div>
                ))}
            </div>
          )}
        </div>
      </motion.div>
    </div>
  );
}
