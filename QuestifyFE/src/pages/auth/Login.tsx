import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useAuthContext } from '../../contexts/AuthContext';
import { useNavigate, useLocation } from 'react-router-dom';


const schema = z.object({
    usernameOrEmail: z.string().min(3, 'Enter username or email'),
    password: z.string().min(6, 'Password is too short'),
    });


    type FormValues = z.infer<typeof schema>;


    export default function Login() {
    const { login } = useAuthContext();
    const navigate = useNavigate();
    const location = useLocation();
    const from = (location.state as any)?.from?.pathname || '/';
    const [error, setError] = useState<string | null>(null);


    const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormValues>({
        resolver: zodResolver(schema),
    });


    const onSubmit = async (values: FormValues) => {
        setError(null);
        try {
        await login(values);
        navigate(from, { replace: true });
        } catch (e: any) {
        setError(e?.response?.data?.message ?? 'Login failed');
        }
    };


return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-gray-50">
        <div className="w-full max-w-md rounded-2xl shadow p-6 bg-white">
    <h1 className="text-2xl font-semibold mb-4">Sign in</h1>
        {error && <div className="mb-3 text-sm text-red-600">{error}</div>}
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
    <div>
        <label className="block text-sm mb-1">Username or Email</label>
        <input {...register('usernameOrEmail')} className="w-full border rounded-xl px-3 py-2" />
        {errors.usernameOrEmail && <p className="text-xs text-red-600 mt-1">{errors.usernameOrEmail.message}</p>}
    </div>
    <div>
        <label className="block text-sm mb-1">Password</label>
        <input type="password" {...register('password')} className="w-full border rounded-xl px-3 py-2" />
        {errors.password && <p className="text-xs text-red-600 mt-1">{errors.password.message}</p>}
    </div>
        <button disabled={isSubmitting} className="w-full rounded-2xl py-2 border hover:shadow">
        {isSubmitting ? 'Signing in…' : 'Sign in'}
        </button>
    </form>
        </div>
    </div>
);
}