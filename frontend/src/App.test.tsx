import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('OmniSmart foundation', () => {
  it('presents the MVP workflow and human approval principle', () => {
    render(<App />)

    expect(
      screen.getByRole('heading', {
        name: /một quy trình rõ ràng từ sản phẩm đến nội dung đã duyệt/i,
      }),
    ).toBeInTheDocument()
    expect(screen.getByText('AI tạo bản nháp')).toBeInTheDocument()
    expect(screen.getByText('Human approval by default')).toBeInTheDocument()
  })
})
