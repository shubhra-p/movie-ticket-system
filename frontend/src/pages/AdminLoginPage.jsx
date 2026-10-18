import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useAuth } from '../context/AuthContext';
import { Mail, Lock, ArrowRight, Shield, AlertCircle } from 'lucide-react';
import './Auth.css';
import './AdminLogin.css';

export default function AdminLoginPage() {
  const { login, loading, error, clearError } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [roleError, setRoleError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setRoleError(null);
    try {
      const data = await login(email, password);
      if (data?.user?.role !== 'ADMIN') {
        setRoleError('Access denied. This account does not have admin privileges.');
        return;
      }
      navigate('/admin/dashboard');
    } catch {}
  };

  return (
    <div className="auth-page admin-login-page">
      <div className="auth-bg-pattern admin-bg-pattern" />
      <motion.div
        className="auth-card glass admin-login-card"
        initial={{ opacity: 0, y: 30, scale: 0.96 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
      >
        <div className="auth-header">
          <div className="admin-icon-wrapper">
            <Shield size={36} strokeWidth={2} />
          </div>
          <h1>Admin Portal</h1>
          <p>Sign in with your admin credentials</p>
        </div>

        {(error || roleError) && (
          <motion.div
            className="auth-error"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
          >
            <AlertCircle size={16} style={{ marginRight: 8, verticalAlign: 'middle' }} />
            {roleError || error}
          </motion.div>
        )}

        <form onSubmit={handleSubmit} className="auth-form">
          <div className="input-group">
            <label htmlFor="admin-email">Email</label>
            <div className="input-icon-wrapper">
              <Mail size={16} className="input-icon" />
              <input
                id="admin-email"
                type="email"
                className="input input-with-icon"
                placeholder="admin@cinebook.com"
                value={email}
                onChange={(e) => { setEmail(e.target.value); clearError(); setRoleError(null); }}
                required
              />
            </div>
          </div>

          <div className="input-group">
            <label htmlFor="admin-password">Password</label>
            <div className="input-icon-wrapper">
              <Lock size={16} className="input-icon" />
              <input
                id="admin-password"
                type="password"
                className="input input-with-icon"
                placeholder="••••••••"
                value={password}
                onChange={(e) => { setPassword(e.target.value); clearError(); setRoleError(null); }}
                required
              />
            </div>
          </div>

          <button type="submit" className="btn btn-primary btn-lg admin-login-btn" disabled={loading} id="admin-login-submit">
            {loading ? <span className="spinner" /> : <><span>Sign In as Admin</span><ArrowRight size={18} /></>}
          </button>
        </form>

        <p className="auth-footer" style={{ opacity: 0.5, fontSize: '0.82rem' }}>
          Restricted area — authorized personnel only
        </p>
      </motion.div>
    </div>
  );
}
