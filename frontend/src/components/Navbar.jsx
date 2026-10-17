import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Film, Ticket, LogOut, User } from 'lucide-react';
import { motion } from 'framer-motion';
import './Navbar.css';

export default function Navbar() {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const isActive = (path) => location.pathname === path || location.pathname.startsWith(path + '/');

  return (
    <motion.nav
      className="navbar glass"
      initial={{ y: -60, opacity: 0 }}
      animate={{ y: 0, opacity: 1 }}
      transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
    >
      <div className="navbar-inner container">
        <Link to="/" className="navbar-brand">
          <Film size={22} strokeWidth={2.5} />
          <span className="gradient-text">CineBook</span>
        </Link>

        <div className="navbar-links">
          {isAuthenticated ? (
            <>
              <Link to="/movies" className={`nav-link ${isActive('/movies') ? 'active' : ''}`}>
                <Film size={16} />
                Movies
              </Link>
              <Link to="/my-tickets" className={`nav-link ${isActive('/my-tickets') ? 'active' : ''}`}>
                <Ticket size={16} />
                My Tickets
              </Link>
              <div className="nav-divider" />
              <div className="nav-user">
                <User size={15} />
                <span>{user?.name || user?.email}</span>
              </div>
              <button onClick={handleLogout} className="btn btn-ghost btn-sm" id="logout-btn">
                <LogOut size={15} />
                Logout
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="btn btn-ghost btn-sm">Sign In</Link>
              <Link to="/register" className="btn btn-primary btn-sm">Get Started</Link>
            </>
          )}
        </div>
      </div>
    </motion.nav>
  );
}
