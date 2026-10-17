import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { movies as moviesApi } from '../api';
import { Search, Clock, Calendar, ChevronRight } from 'lucide-react';
import './MoviesPage.css';

const GENRE_COLORS = {
  Action: '#ef4444',
  Comedy: '#f59e0b',
  Drama: '#8b5cf6',
  Horror: '#dc2626',
  Romance: '#ec4899',
  'Sci-Fi': '#06b6d4',
  Thriller: '#f97316',
  Animation: '#10b981',
};

export default function MoviesPage() {
  const [moviesList, setMoviesList] = useState([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadMovies();
  }, []);

  const loadMovies = async (query) => {
    setLoading(true);
    setError(null);
    try {
      const data = await moviesApi.list(query);
      setMoviesList(data || []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = (e) => {
    e.preventDefault();
    loadMovies(search || undefined);
  };

  const container = {
    hidden: {},
    show: { transition: { staggerChildren: 0.06 } },
  };

  const item = {
    hidden: { opacity: 0, y: 20 },
    show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.22, 1, 0.36, 1] } },
  };

  return (
    <div className="movies-page container">
      <motion.div
        className="movies-header"
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
      >
        <div>
          <h1>Now Showing</h1>
          <p className="movies-subtitle">Pick a movie, grab your seats</p>
        </div>
        <form className="movies-search" onSubmit={handleSearch}>
          <Search size={18} className="search-icon" />
          <input
            id="movie-search"
            type="text"
            className="input"
            placeholder="Search movies..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </form>
      </motion.div>

      {error && <div className="auth-error" style={{ marginBottom: 24 }}>{error}</div>}

      {loading ? (
        <div className="movies-loading">
          <div className="spinner spinner-lg" />
        </div>
      ) : moviesList.length === 0 ? (
        <motion.div
          className="movies-empty"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
        >
          <p>No movies found. Try a different search.</p>
        </motion.div>
      ) : (
        <motion.div
          className="movies-grid"
          variants={container}
          initial="hidden"
          animate="show"
        >
          {moviesList.map((movie) => (
            <motion.div key={movie.id} variants={item}>
              <Link to={`/movies/${movie.id}`} className="movie-card card" id={`movie-${movie.id}`}>
                <div className="movie-poster" style={{
                  background: `linear-gradient(135deg, ${GENRE_COLORS[movie.genre] || '#6366f1'}20, ${GENRE_COLORS[movie.genre] || '#6366f1'}08)`,
                }}>
                  <div className="movie-poster-icon" style={{ color: GENRE_COLORS[movie.genre] || '#6366f1' }}>
                    {movie.title.charAt(0)}
                  </div>
                </div>
                <div className="movie-info">
                  <h3 className="movie-title">{movie.title}</h3>
                  <div className="movie-meta">
                    <span className="badge badge-neutral">{movie.genre}</span>
                    <span className="movie-meta-item">
                      <Clock size={13} />
                      {movie.durationMinutes}m
                    </span>
                    {movie.language && (
                      <span className="movie-meta-item">{movie.language}</span>
                    )}
                  </div>
                  {movie.description && (
                    <p className="movie-desc">{movie.description}</p>
                  )}
                  <div className="movie-cta">
                    <span>View Showtimes</span>
                    <ChevronRight size={16} />
                  </div>
                </div>
              </Link>
            </motion.div>
          ))}
        </motion.div>
      )}
    </div>
  );
}
