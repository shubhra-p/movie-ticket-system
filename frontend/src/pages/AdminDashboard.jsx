import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  movies as moviesApi,
  theaters as theatersApi,
} from '../api';
import {
  Film, Building2, CalendarClock, Plus, X, Check, AlertCircle,
  Clock, Tag, Globe, Image, Type, AlignLeft, ChevronDown,
  MapPin, Monitor, Armchair, DollarSign, Calendar, LogOut, Shield
} from 'lucide-react';
import './AdminDashboard.css';

/* ========== Constants ========== */
const GENRES = [
  'Action', 'Comedy', 'Drama', 'Horror', 'Romance',
  'Sci-Fi', 'Thriller', 'Animation', 'Adventure', 'Fantasy',
  'Mystery', 'Documentary',
];
const LANGUAGES = [
  'English', 'Hindi', 'Spanish', 'French', 'Korean',
  'Japanese', 'Tamil', 'Telugu', 'German', 'Chinese',
];
const SEAT_CATEGORIES = ['REGULAR', 'PREMIUM', 'VIP'];

const TABS = [
  { key: 'movies', label: 'Movies', icon: Film },
  { key: 'theaters', label: 'Theaters', icon: Building2 },
  { key: 'schedules', label: 'Showtimes', icon: CalendarClock },
];

/* ========== Main Dashboard ========== */
export default function AdminDashboard() {
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('movies');

  const handleLogout = () => {
    logout();
    navigate('/admin');
  };

  return (
    <div className="admin-dashboard">
      {/* Sidebar */}
      <motion.aside
        className="admin-sidebar glass"
        initial={{ x: -60, opacity: 0 }}
        animate={{ x: 0, opacity: 1 }}
        transition={{ duration: 0.4 }}
      >
        <div className="sidebar-brand">
          <Shield size={24} />
          <span>CineBook Admin</span>
        </div>

        <nav className="sidebar-nav">
          {TABS.map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              className={`sidebar-nav-item ${activeTab === key ? 'active' : ''}`}
              onClick={() => setActiveTab(key)}
              id={`tab-${key}`}
            >
              <Icon size={20} />
              <span>{label}</span>
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="sidebar-user">
            <div className="sidebar-user-avatar">
              {(user?.name || 'A').charAt(0).toUpperCase()}
            </div>
            <div className="sidebar-user-info">
              <span className="sidebar-user-name">{user?.name || 'Admin'}</span>
              <span className="sidebar-user-role">Administrator</span>
            </div>
          </div>
          <button className="btn btn-ghost btn-sm" onClick={handleLogout} id="admin-logout-btn">
            <LogOut size={16} />
            Logout
          </button>
        </div>
      </motion.aside>

      {/* Main Content */}
      <main className="admin-main">
        <AnimatePresence mode="wait">
          {activeTab === 'movies' && <MoviesTab key="movies" />}
          {activeTab === 'theaters' && <TheatersTab key="theaters" />}
          {activeTab === 'schedules' && <SchedulesTab key="schedules" />}
        </AnimatePresence>
      </main>
    </div>
  );
}

/* ========== MOVIES TAB ========== */
function MoviesTab() {
  const [moviesList, setMoviesList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({
    title: '', description: '', genre: '', durationMinutes: '',
    language: '', posterUrl: '', releaseDate: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [posterPreview, setPosterPreview] = useState('');

  useEffect(() => { loadMovies(); }, []);

  useEffect(() => {
    const t = setTimeout(() => setPosterPreview(form.posterUrl), 400);
    return () => clearTimeout(t);
  }, [form.posterUrl]);

  const loadMovies = async () => {
    setLoading(true);
    try {
      const data = await moviesApi.list();
      setMoviesList(data || []);
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  };

  const handleChange = (field, value) => {
    setForm(prev => ({ ...prev, [field]: value }));
    setError(null); setSuccess(null);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true); setError(null); setSuccess(null);
    try {
      await moviesApi.create({
        ...form,
        durationMinutes: parseInt(form.durationMinutes) || 0,
      });
      setSuccess(`"${form.title}" added successfully!`);
      setForm({ title: '', description: '', genre: '', durationMinutes: '', language: '', posterUrl: '', releaseDate: '' });
      setPosterPreview('');
      loadMovies();
      setTimeout(() => setShowForm(false), 1200);
    } catch (err) { setError(err.message); }
    finally { setSubmitting(false); }
  };

  return (
    <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -16 }} transition={{ duration: 0.3 }}>
      <div className="tab-header">
        <div>
          <h1>Movies</h1>
          <p className="tab-subtitle">Manage your movie catalog</p>
        </div>
        <button className={`btn ${showForm ? 'btn-secondary' : 'btn-primary'}`} onClick={() => { setShowForm(!showForm); setError(null); setSuccess(null); }}>
          {showForm ? <><X size={18} /> Cancel</> : <><Plus size={18} /> Add Movie</>}
        </button>
      </div>

      <AnimatePresence>
        {showForm && (
          <motion.div className="form-panel glass" initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }} transition={{ duration: 0.35 }}>
            <h2 className="form-panel-title"><Film size={20} /> Add New Movie</h2>
            <StatusMessages error={error} success={success} />
            <form onSubmit={handleSubmit} className="admin-form">
              <div className="form-grid">
                <FormInput id="movie-title" label="Movie Title" icon={Type} placeholder="e.g. Inception" value={form.title} onChange={v => handleChange('title', v)} required span2 />
                <FormSelect id="movie-genre" label="Genre" icon={Tag} value={form.genre} onChange={v => handleChange('genre', v)} options={GENRES} placeholder="Select genre..." required />
                <FormSelect id="movie-language" label="Language" icon={Globe} value={form.language} onChange={v => handleChange('language', v)} options={LANGUAGES} placeholder="Select language..." />
                <FormInput id="movie-duration" label="Duration (min)" icon={Clock} type="number" placeholder="e.g. 148" value={form.durationMinutes} onChange={v => handleChange('durationMinutes', v)} required min="1" max="600" />
                <FormInput id="movie-release" label="Release Date" icon={Calendar} type="date" value={form.releaseDate} onChange={v => handleChange('releaseDate', v)} required />
                <div className="input-group form-span-2">
                  <label htmlFor="movie-desc"><AlignLeft size={14} /> Description</label>
                  <textarea id="movie-desc" className="input textarea" placeholder="Brief synopsis..." rows={3} value={form.description} onChange={e => handleChange('description', e.target.value)} />
                </div>
                <div className="input-group form-span-2">
                  <label htmlFor="movie-poster"><Image size={14} /> Poster Image URL</label>
                  <input id="movie-poster" type="url" className="input" placeholder="https://image.tmdb.org/t/p/w500/..." value={form.posterUrl} onChange={e => handleChange('posterUrl', e.target.value)} />
                  {posterPreview && (
                    <motion.div className="poster-preview" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
                      <img src={posterPreview} alt="Preview" onError={e => e.target.style.display = 'none'} onLoad={e => e.target.style.display = 'block'} />
                    </motion.div>
                  )}
                </div>
              </div>
              <button type="submit" className="btn btn-primary btn-lg form-submit-btn" disabled={submitting}>
                {submitting ? <span className="spinner" /> : <><Plus size={18} /> Add Movie</>}
              </button>
            </form>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Movie List */}
      <div className="entity-list-header">
        <h2>All Movies <span className="count-badge">{moviesList.length}</span></h2>
      </div>
      {loading ? <Loading /> : moviesList.length === 0 ? (
        <EmptyState icon={Film} message="No movies yet. Add your first movie above!" />
      ) : (
        <div className="entity-grid entity-grid-movies">
          {moviesList.map(movie => (
            <div key={movie.id} className="entity-card card" id={`admin-movie-${movie.id}`}>
              <div className="entity-card-poster">
                {movie.posterUrl ? (
                  <img src={movie.posterUrl} alt={movie.title} onError={e => { e.target.style.display = 'none'; e.target.nextSibling.style.display = 'flex'; }} />
                ) : null}
                <div className="entity-card-poster-fallback" style={{ display: movie.posterUrl ? 'none' : 'flex' }}>
                  <Film size={32} strokeWidth={1.5} />
                </div>
              </div>
              <div className="entity-card-body">
                <h3>{movie.title}</h3>
                <div className="entity-card-meta">
                  <span className="badge badge-neutral">{movie.genre}</span>
                  <span className="meta-item"><Clock size={13} /> {movie.durationMinutes}m</span>
                  {movie.language && <span className="meta-item">{movie.language}</span>}
                </div>
                {movie.description && <p className="entity-card-desc">{movie.description}</p>}
              </div>
            </div>
          ))}
        </div>
      )}
    </motion.div>
  );
}

/* ========== THEATERS TAB ========== */
function TheatersTab() {
  const [theatersList, setTheatersList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ name: '', city: '', address: '', screens: [createEmptyScreen()] });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  useEffect(() => { loadTheaters(); }, []);

  const loadTheaters = async () => {
    setLoading(true);
    try {
      const data = await theatersApi.list();
      setTheatersList(data || []);
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  };

  const handleChange = (field, value) => {
    setForm(prev => ({ ...prev, [field]: value }));
    setError(null); setSuccess(null);
  };

  /* Screen management */
  const addScreen = () => {
    setForm(prev => ({ ...prev, screens: [...prev.screens, createEmptyScreen()] }));
  };

  const removeScreen = (idx) => {
    setForm(prev => ({ ...prev, screens: prev.screens.filter((_, i) => i !== idx) }));
  };

  const updateScreen = (idx, field, value) => {
    setForm(prev => {
      const screens = [...prev.screens];
      screens[idx] = { ...screens[idx], [field]: value };
      return { ...prev, screens };
    });
  };

  /* Seat layout management within a screen */
  const addLayout = (screenIdx) => {
    setForm(prev => {
      const screens = [...prev.screens];
      screens[screenIdx] = {
        ...screens[screenIdx],
        seatLayouts: [...screens[screenIdx].seatLayouts, createEmptyLayout()],
      };
      return { ...prev, screens };
    });
  };

  const removeLayout = (screenIdx, layoutIdx) => {
    setForm(prev => {
      const screens = [...prev.screens];
      screens[screenIdx] = {
        ...screens[screenIdx],
        seatLayouts: screens[screenIdx].seatLayouts.filter((_, i) => i !== layoutIdx),
      };
      return { ...prev, screens };
    });
  };

  const updateLayout = (screenIdx, layoutIdx, field, value) => {
    setForm(prev => {
      const screens = [...prev.screens];
      const layouts = [...screens[screenIdx].seatLayouts];
      layouts[layoutIdx] = { ...layouts[layoutIdx], [field]: value };
      screens[screenIdx] = { ...screens[screenIdx], seatLayouts: layouts };
      return { ...prev, screens };
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true); setError(null); setSuccess(null);
    try {
      const payload = {
        name: form.name,
        city: form.city,
        address: form.address,
        screens: form.screens.map(s => ({
          name: s.name,
          seatLayouts: s.seatLayouts.map(l => ({
            category: l.category,
            rowPrefix: l.rowPrefix,
            rowCount: parseInt(l.rowCount) || 1,
            seatsPerRow: parseInt(l.seatsPerRow) || 1,
            price: parseFloat(l.price) || 0,
          })),
        })),
      };
      await theatersApi.create(payload);
      setSuccess(`"${form.name}" theater created successfully!`);
      setForm({ name: '', city: '', address: '', screens: [createEmptyScreen()] });
      loadTheaters();
      setTimeout(() => setShowForm(false), 1200);
    } catch (err) { setError(err.message); }
    finally { setSubmitting(false); }
  };

  return (
    <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -16 }} transition={{ duration: 0.3 }}>
      <div className="tab-header">
        <div>
          <h1>Theaters</h1>
          <p className="tab-subtitle">Manage theaters, screens & seating</p>
        </div>
        <button className={`btn ${showForm ? 'btn-secondary' : 'btn-primary'}`} onClick={() => { setShowForm(!showForm); setError(null); setSuccess(null); }}>
          {showForm ? <><X size={18} /> Cancel</> : <><Plus size={18} /> Add Theater</>}
        </button>
      </div>

      <AnimatePresence>
        {showForm && (
          <motion.div className="form-panel glass" initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }} transition={{ duration: 0.35 }}>
            <h2 className="form-panel-title"><Building2 size={20} /> Add New Theater</h2>
            <StatusMessages error={error} success={success} />
            <form onSubmit={handleSubmit} className="admin-form">
              <div className="form-grid">
                <FormInput id="theater-name" label="Theater Name" icon={Building2} placeholder="e.g. PVR Cinemas" value={form.name} onChange={v => handleChange('name', v)} required span2 />
                <FormInput id="theater-city" label="City" icon={MapPin} placeholder="e.g. Mumbai" value={form.city} onChange={v => handleChange('city', v)} required />
                <FormInput id="theater-address" label="Address" icon={MapPin} placeholder="e.g. 123 Main Street" value={form.address} onChange={v => handleChange('address', v)} />
              </div>

              {/* Screens */}
              <div className="screens-section">
                <div className="screens-section-header">
                  <h3><Monitor size={18} /> Screens</h3>
                  <button type="button" className="btn btn-ghost btn-sm" onClick={addScreen}>
                    <Plus size={16} /> Add Screen
                  </button>
                </div>

                {form.screens.map((screen, sIdx) => (
                  <div key={sIdx} className="screen-block card">
                    <div className="screen-block-header">
                      <h4>Screen {sIdx + 1}</h4>
                      {form.screens.length > 1 && (
                        <button type="button" className="btn btn-ghost btn-sm remove-btn" onClick={() => removeScreen(sIdx)}>
                          <X size={14} /> Remove
                        </button>
                      )}
                    </div>
                    <div className="form-grid" style={{ marginBottom: 16 }}>
                      <FormInput id={`screen-${sIdx}-name`} label="Screen Name" icon={Monitor} placeholder="e.g. Screen 1" value={screen.name} onChange={v => updateScreen(sIdx, 'name', v)} required />
                    </div>

                    {/* Seat Layouts */}
                    <div className="layouts-header">
                      <h5><Armchair size={15} /> Seat Sections</h5>
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => addLayout(sIdx)}>
                        <Plus size={14} /> Add Section
                      </button>
                    </div>

                    {screen.seatLayouts.map((layout, lIdx) => (
                      <div key={lIdx} className="layout-row">
                        <FormSelect
                          id={`layout-${sIdx}-${lIdx}-cat`}
                          label="Category"
                          icon={Tag}
                          value={layout.category}
                          onChange={v => updateLayout(sIdx, lIdx, 'category', v)}
                          options={SEAT_CATEGORIES}
                          placeholder="Category..."
                          required
                          small
                        />
                        <FormInput
                          id={`layout-${sIdx}-${lIdx}-prefix`}
                          label="Row Prefix"
                          icon={Type}
                          placeholder="e.g. A"
                          value={layout.rowPrefix}
                          onChange={v => updateLayout(sIdx, lIdx, 'rowPrefix', v)}
                          required
                          small
                        />
                        <FormInput
                          id={`layout-${sIdx}-${lIdx}-rows`}
                          label="Rows"
                          icon={AlignLeft}
                          type="number"
                          placeholder="3"
                          value={layout.rowCount}
                          onChange={v => updateLayout(sIdx, lIdx, 'rowCount', v)}
                          required
                          min="1"
                          small
                        />
                        <FormInput
                          id={`layout-${sIdx}-${lIdx}-seats`}
                          label="Seats/Row"
                          icon={Armchair}
                          type="number"
                          placeholder="10"
                          value={layout.seatsPerRow}
                          onChange={v => updateLayout(sIdx, lIdx, 'seatsPerRow', v)}
                          required
                          min="1"
                          small
                        />
                        <FormInput
                          id={`layout-${sIdx}-${lIdx}-price`}
                          label="Price (₹)"
                          icon={DollarSign}
                          type="number"
                          placeholder="200"
                          value={layout.price}
                          onChange={v => updateLayout(sIdx, lIdx, 'price', v)}
                          required
                          min="1"
                          small
                          step="0.01"
                        />
                        {screen.seatLayouts.length > 1 && (
                          <button type="button" className="layout-remove-btn" onClick={() => removeLayout(sIdx, lIdx)} title="Remove section">
                            <X size={16} />
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                ))}
              </div>

              <button type="submit" className="btn btn-primary btn-lg form-submit-btn" disabled={submitting}>
                {submitting ? <span className="spinner" /> : <><Plus size={18} /> Create Theater</>}
              </button>
            </form>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Theaters List */}
      <div className="entity-list-header">
        <h2>All Theaters <span className="count-badge">{theatersList.length}</span></h2>
      </div>
      {loading ? <Loading /> : theatersList.length === 0 ? (
        <EmptyState icon={Building2} message="No theaters yet. Create your first theater above!" />
      ) : (
        <div className="entity-grid entity-grid-theaters">
          {theatersList.map(theater => (
            <div key={theater.id} className="entity-card card theater-card" id={`admin-theater-${theater.id}`}>
              <div className="entity-card-body">
                <h3>{theater.name}</h3>
                <div className="entity-card-meta">
                  <span className="meta-item"><MapPin size={14} /> {theater.city}</span>
                  {theater.address && <span className="meta-item" style={{ opacity: 0.7 }}>{theater.address}</span>}
                </div>
                {theater.screens && theater.screens.length > 0 && (
                  <div className="theater-screens-list">
                    {theater.screens.map(screen => (
                      <div key={screen.id} className="theater-screen-chip">
                        <Monitor size={13} />
                        <span>{screen.name}</span>
                        <span className="screen-seats">{screen.totalSeats} seats</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </motion.div>
  );
}

/* ========== SCHEDULES TAB ========== */
function SchedulesTab() {
  const [moviesList, setMoviesList] = useState([]);
  const [theatersList, setTheatersList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ movieId: '', screenId: '', showDate: '', showTime: '' });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [schedulesList, setSchedulesList] = useState([]);

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const [moviesData, theatersData] = await Promise.all([
        moviesApi.list(),
        theatersApi.list(),
      ]);
      setMoviesList(moviesData || []);
      setTheatersList(theatersData || []);

      // Load schedules for all movies
      const allSchedules = [];
      for (const movie of (moviesData || [])) {
        try {
          const sched = await moviesApi.schedules(movie.id);
          if (sched) allSchedules.push(...sched);
        } catch {}
      }
      setSchedulesList(allSchedules);
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  };

  const handleChange = (field, value) => {
    setForm(prev => ({ ...prev, [field]: value }));
    setError(null); setSuccess(null);
  };

  // Build flat list of screens from theaters
  const allScreens = theatersList.flatMap(t =>
    (t.screens || []).map(s => ({
      ...s,
      theaterId: t.id,
      theaterName: t.name,
      theaterCity: t.city,
      label: `${t.name} — ${s.name} (${s.totalSeats} seats)`,
    }))
  );

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true); setError(null); setSuccess(null);
    try {
      await moviesApi.createSchedule({
        movieId: form.movieId,
        screenId: form.screenId,
        showDate: form.showDate,
        showTime: form.showTime + ':00',  // Backend expects HH:mm:ss
      });
      const selectedMovie = moviesList.find(m => m.id === form.movieId);
      setSuccess(`Showtime for "${selectedMovie?.title || 'movie'}" created!`);
      setForm({ movieId: '', screenId: '', showDate: '', showTime: '' });
      loadData();
      setTimeout(() => setShowForm(false), 1200);
    } catch (err) { setError(err.message); }
    finally { setSubmitting(false); }
  };

  const formatTime = (t) => {
    if (!t) return '';
    const [h, m] = t.toString().split(':');
    const hour = parseInt(h);
    return `${hour % 12 || 12}:${m} ${hour >= 12 ? 'PM' : 'AM'}`;
  };

  return (
    <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -16 }} transition={{ duration: 0.3 }}>
      <div className="tab-header">
        <div>
          <h1>Showtimes</h1>
          <p className="tab-subtitle">Schedule movies in theaters</p>
        </div>
        <button className={`btn ${showForm ? 'btn-secondary' : 'btn-primary'}`} onClick={() => { setShowForm(!showForm); setError(null); setSuccess(null); }}>
          {showForm ? <><X size={18} /> Cancel</> : <><Plus size={18} /> Add Showtime</>}
        </button>
      </div>

      <AnimatePresence>
        {showForm && (
          <motion.div className="form-panel glass" initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }} transition={{ duration: 0.35 }}>
            <h2 className="form-panel-title"><CalendarClock size={20} /> Schedule a Showtime</h2>
            <StatusMessages error={error} success={success} />

            {moviesList.length === 0 || allScreens.length === 0 ? (
              <div className="form-prereq-warning">
                <AlertCircle size={18} />
                <span>
                  {moviesList.length === 0 && 'You need to add movies first. '}
                  {allScreens.length === 0 && 'You need to create theaters with screens first.'}
                </span>
              </div>
            ) : (
              <form onSubmit={handleSubmit} className="admin-form">
                <div className="form-grid">
                  {/* Movie select */}
                  <div className="input-group">
                    <label htmlFor="sched-movie"><Film size={14} /> Movie</label>
                    <div className="select-wrapper">
                      <select id="sched-movie" className="input select-input" value={form.movieId} onChange={e => handleChange('movieId', e.target.value)} required>
                        <option value="">Select movie...</option>
                        {moviesList.map(m => <option key={m.id} value={m.id}>{m.title} ({m.genre})</option>)}
                      </select>
                      <ChevronDown size={16} className="select-chevron" />
                    </div>
                  </div>

                  {/* Screen select */}
                  <div className="input-group">
                    <label htmlFor="sched-screen"><Monitor size={14} /> Screen</label>
                    <div className="select-wrapper">
                      <select id="sched-screen" className="input select-input" value={form.screenId} onChange={e => handleChange('screenId', e.target.value)} required>
                        <option value="">Select screen...</option>
                        {allScreens.map(s => <option key={s.id} value={s.id}>{s.label}</option>)}
                      </select>
                      <ChevronDown size={16} className="select-chevron" />
                    </div>
                  </div>

                  <FormInput id="sched-date" label="Show Date" icon={Calendar} type="date" value={form.showDate} onChange={v => handleChange('showDate', v)} required />
                  <FormInput id="sched-time" label="Show Time" icon={Clock} type="time" value={form.showTime} onChange={v => handleChange('showTime', v)} required />
                </div>

                <button type="submit" className="btn btn-primary btn-lg form-submit-btn" disabled={submitting}>
                  {submitting ? <span className="spinner" /> : <><Plus size={18} /> Create Showtime</>}
                </button>
              </form>
            )}
          </motion.div>
        )}
      </AnimatePresence>

      {/* Schedules List */}
      <div className="entity-list-header">
        <h2>All Showtimes <span className="count-badge">{schedulesList.length}</span></h2>
      </div>
      {loading ? <Loading /> : schedulesList.length === 0 ? (
        <EmptyState icon={CalendarClock} message="No showtimes yet. Schedule your first showtime above!" />
      ) : (
        <div className="schedules-table-wrapper">
          <table className="schedules-table">
            <thead>
              <tr>
                <th>Movie</th>
                <th>Theater</th>
                <th>Screen</th>
                <th>Date</th>
                <th>Time</th>
                <th>Seats</th>
              </tr>
            </thead>
            <tbody>
              {schedulesList
                .sort((a, b) => {
                  const dateComp = (a.showDate || '').localeCompare(b.showDate || '');
                  if (dateComp !== 0) return dateComp;
                  return (a.showTime || '').localeCompare(b.showTime || '');
                })
                .map(sched => (
                <tr key={sched.id} id={`sched-${sched.id}`}>
                  <td className="sched-movie-cell">{sched.movieTitle}</td>
                  <td>{sched.theaterName || '—'}</td>
                  <td>{sched.screenName}</td>
                  <td>{sched.showDate ? new Date(sched.showDate + 'T00:00:00').toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' }) : '—'}</td>
                  <td className="sched-time-cell">{formatTime(sched.showTime)}</td>
                  <td><span className="badge badge-neutral">{sched.totalSeats}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </motion.div>
  );
}

/* ========== Shared Components ========== */
function FormInput({ id, label, icon: Icon, type = 'text', placeholder, value, onChange, required, span2, small, ...rest }) {
  return (
    <div className={`input-group ${span2 ? 'form-span-2' : ''} ${small ? 'form-small' : ''}`}>
      <label htmlFor={id}><Icon size={14} /> {label}</label>
      <input id={id} type={type} className="input" placeholder={placeholder} value={value} onChange={e => onChange(e.target.value)} required={required} {...rest} />
    </div>
  );
}

function FormSelect({ id, label, icon: Icon, value, onChange, options, placeholder, required, small }) {
  return (
    <div className={`input-group ${small ? 'form-small' : ''}`}>
      <label htmlFor={id}><Icon size={14} /> {label}</label>
      <div className="select-wrapper">
        <select id={id} className="input select-input" value={value} onChange={e => onChange(e.target.value)} required={required}>
          <option value="">{placeholder}</option>
          {options.map(o => <option key={o} value={o}>{o}</option>)}
        </select>
        <ChevronDown size={16} className="select-chevron" />
      </div>
    </div>
  );
}

function StatusMessages({ error, success }) {
  return (
    <>
      {error && (
        <motion.div className="auth-error" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
          <AlertCircle size={16} style={{ marginRight: 8, verticalAlign: 'middle' }} />{error}
        </motion.div>
      )}
      {success && (
        <motion.div className="admin-success" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
          <Check size={16} style={{ marginRight: 8, verticalAlign: 'middle' }} />{success}
        </motion.div>
      )}
    </>
  );
}

function Loading() {
  return <div className="movies-loading"><div className="spinner spinner-lg" /></div>;
}

function EmptyState({ icon: Icon, message }) {
  return (
    <motion.div className="empty-state" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
      <Icon size={48} strokeWidth={1.5} />
      <p>{message}</p>
    </motion.div>
  );
}

/* ========== Helpers ========== */
function createEmptyScreen() {
  return { name: '', seatLayouts: [createEmptyLayout()] };
}

function createEmptyLayout() {
  return { category: 'REGULAR', rowPrefix: '', rowCount: '', seatsPerRow: '', price: '' };
}
