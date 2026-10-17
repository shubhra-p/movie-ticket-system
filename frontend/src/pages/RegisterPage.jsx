import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useAuth } from '../context/AuthContext';
import { Mail, Lock, User, ArrowRight, Film } from 'lucide-react';
import './Auth.css';

export default function RegisterPage() {
  const { register, loading, error, clearError } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      await register(name, email, password);
      navigate('/movies');
    } catch {}
  };

  return (
    <div className="auth-page">
      <div className="auth-bg-pattern" />
      <motion.div
        className="auth-card glass"
        initial={{ opacity: 0, y: 30, scale: 0.96 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
      >
        <div className="auth-header">
          <Film size={32} className="auth-icon" />
          <h1>Create Account</h1>
          <p>Join CineBook and start booking</p>
        </div>

        {error && (
          <motion.div
            className="auth-error"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
          >
            {error}
          </motion.div>
        )}

        <form onSubmit={handleSubmit} className="auth-form">
          <div className="input-group">
            <label htmlFor="register-name">Full Name</label>
            <div className="input-icon-wrapper">
              <User size={16} className="input-icon" />
              <input
                id="register-name"
                type="text"
                className="input input-with-icon"
                placeholder="John Doe"
                value={name}
                onChange={(e) => { setName(e.target.value); clearError(); }}
                required
              />
            </div>
          </div>

          <div className="input-group">
            <label htmlFor="register-email">Email</label>
            <div className="input-icon-wrapper">
              <Mail size={16} className="input-icon" />
              <input
                id="register-email"
                type="email"
                className="input input-with-icon"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => { setEmail(e.target.value); clearError(); }}
                required
              />
            </div>
          </div>

          <div className="input-group">
            <label htmlFor="register-password">Password</label>
            <div className="input-icon-wrapper">
              <Lock size={16} className="input-icon" />
              <input
                id="register-password"
                type="password"
                className="input input-with-icon"
                placeholder="Min 6 characters"
                value={password}
                onChange={(e) => { setPassword(e.target.value); clearError(); }}
                required
                minLength={6}
              />
            </div>
          </div>

          <button type="submit" className="btn btn-primary btn-lg" disabled={loading} id="register-submit">
            {loading ? <span className="spinner" /> : <><span>Create Account</span><ArrowRight size={18} /></>}
          </button>
        </form>

        <p className="auth-footer">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </motion.div>
    </div>
  );
}
